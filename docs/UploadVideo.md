# UploadVideo.md — Luồng Upload & Xử lý Video Giảng viên

**Phiên bản:** 1.0 | **Stack:** Java 21, Spring Boot 3.3.5, MinIO, FFmpeg, RabbitMQ (AMQP)  
**Môi trường:** Local Dev (MinIO thay GCP Cloud Storage)

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Lựa chọn giao thức theo từng bước](#2-lựa-chọn-giao-thức-theo-từng-bước)
3. [Pipeline hoàn chỉnh — Sơ đồ chi tiết](#3-pipeline-hoàn-chỉnh--sơ-đồ-chi-tiết)
4. [Phase 1 — Upload (core-lms-service)](#4-phase-1--upload-core-lms-service)
5. [Phase 2 — Xử lý video (worker-service)](#5-phase-2--xử-lý-video-worker-service)
6. [Phase 3 — Streaming cho học viên](#6-phase-3--streaming-cho-học-viên)
7. [Cấu trúc MinIO Buckets](#7-cấu-trúc-minio-buckets)
8. [Database Schema bổ sung](#8-database-schema-bổ-sung)
9. [Cấu trúc thư mục code](#9-cấu-trúc-thư-mục-code)
10. [Docker Compose — MinIO](#10-docker-compose--minio)
11. [Dependencies POM](#11-dependencies-pom)
12. [Thứ tự triển khai](#12-thứ-tự-triển-khai)
13. [Production Roadmap](#13-production-roadmap)

---

## 1. Tổng quan kiến trúc

Luồng upload video giảng viên được tách thành **3 phase độc lập**, mỗi phase dùng giao thức tối ưu riêng:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PHASE 1: UPLOAD                                                             │
│  REST/HTTP ←→ core-lms-service  →  HTTP PUT ←→ MinIO (oep-raw)             │
├─────────────────────────────────────────────────────────────────────────────┤
│  PHASE 2: PROCESSING                                                         │
│  AMQP ←→ RabbitMQ ←→ worker-service  →  FFmpeg  →  MinIO (oep-hls)        │
│  SSE (progress stream) ←→ Browser                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  PHASE 3: STREAMING                                                          │
│  REST/HTTP ←→ core-lms-service  →  Presigned URL ←→ MinIO (oep-hls)       │
│  HLS (HTTP) ←→ Browser Player (HLS.js / Video.js)                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Lựa chọn giao thức theo từng bước

### Bảng quyết định giao thức

| Bước | Từ → Đến | Giao thức | Lý do chọn |
|------|----------|-----------|------------|
| ① Xin Presigned URL | Browser → core-lms-service | **REST (HTTP POST)** | Request/response đơn giản, có auth JWT |
| ② Upload binary | Browser → MinIO | **HTTP PUT (Presigned URL)** | Không qua API server, tránh OOM/timeout với file lớn |
| ② Upload file lớn | Browser → MinIO | **S3 Multipart Upload (HTTP)** | Retry từng part, progress chính xác, file > 100MB |
| ③ Xác nhận xong | Browser → core-lms-service | **REST (HTTP POST)** | Trigger publish event, idempotent safe |
| ④ Trigger transcode | core-lms-service → worker | **AMQP (RabbitMQ)** | Async durable, worker crash → requeue, DLQ |
| ⑤ Progress transcode | core-lms-service → Browser | **SSE (Server-Sent Events)** | Server push một chiều, HTTP-based, không cần WebSocket |
| ⑥ Lấy stream URL | Browser → core-lms-service | **REST (HTTP GET)** | Kiểm tra enrollment, trả Presigned URL |
| ⑦ Phát video | Browser Player → MinIO | **HLS over HTTP** | Adaptive Bitrate, tương thích mọi thiết bị |

### Tại sao **không dùng WebSocket** cho progress?

| Tiêu chí | WebSocket | SSE (chọn) |
|----------|-----------|-----------|
| Hướng dữ liệu | Bidirectional (2 chiều) | Server → Client (1 chiều) |
| Độ phức tạp | Cao (cần handshake, heartbeat) | Thấp (HTTP thuần) |
| Proxy/CDN | Cần config đặc biệt | Hoạt động qua mọi HTTP proxy |
| Spring hỗ trợ | Spring WebSocket | `SseEmitter` built-in |
| Phù hợp | Chat realtime | **Progress tracking (chọn)** |

> **Kết luận:** SSE đủ mạnh cho progress tracking một chiều. WebSocket là overkill cho use case này.

### Tại sao **giữ RabbitMQ** thay vì `@Async` hay DB polling?

| Giải pháp | Vấn đề |
|-----------|--------|
| `@Async` Spring | Worker restart → **mất hết job** đang chạy trong memory |
| DB polling (scheduler 5s) | N+1 query liên tục, độ trễ cao, lãng phí tài nguyên |
| MinIO Webhook | Phức tạp hơn, vẫn cần buffer để chống tải đột biến |
| **RabbitMQ AMQP (chọn)** | Durable message, auto-requeue khi crash, DLQ, scale N worker |

> Transcoding video 1GB có thể mất **10–30 phút**. HTTP connection không thể giữ mở lâu như vậy → bắt buộc phải async + durable queue.

---

## 3. Pipeline hoàn chỉnh — Sơ đồ chi tiết

```
┌──────────────┐        ┌─────────────────────┐        ┌──────────────┐
│   GIẢNG VIÊN │        │  core-lms-service    │        │     MinIO    │
│   (Browser)  │        │      :8081           │        │     :9000    │
└──────┬───────┘        └──────────┬───────────┘        └──────┬───────┘
       │                           │                            │
       │  ① REST POST /presign     │                            │
       │  { fileName, mimeType,    │                            │
       │    fileSize }             │                            │
       │──────────────────────────►│                            │
       │                           │  Tạo lesson_contents       │
       │                           │  status = PENDING          │
       │                           │  objectKey = raw/{id}/...  │
       │                           │  MinIO SDK: presignedPutUrl│
       │                           │────────────────────────────►
       │  { contentId,             │◄────────────────────────────
       │    presignedUrl,          │
       │    expiresAt }            │
       │◄──────────────────────────│
       │                           │
       │  ② HTTP PUT (binary)      │
       │  → MinIO trực tiếp        │
       │  (KHÔNG qua API server)   │
       │──────────────────────────────────────────────────────►│
       │  200 OK + ETag header     │                            │
       │◄──────────────────────────────────────────────────────│
       │                           │                            │
       │  ③ REST POST /confirm     │                            │
       │  { objectKey }            │                            │
       │──────────────────────────►│                            │
       │                           │  objectExists() verify     │
       │                           │──────────────────────────►│
       │                           │  { size, contentType }     │
       │                           │◄──────────────────────────│
       │                           │                            │
       │                           │  UPDATE lesson_contents    │
       │                           │  status = UPLOADED         │
       │                           │                            │
       │                           │  INSERT video_proc_jobs    │
       │                           │  status = QUEUED           │
       │                           │
       │  { contentId,             │──── AMQP publish ──────────────────────►
       │    status: UPLOADED }     │     VideoUploadedEvent                  │
       │◄──────────────────────────│     (oep.events / video.uploaded)       │
       │                           │                            ┌────────────▼─────────┐
       │                           │                            │   worker-service      │
       │                           │                            │       :8083           │
       │  ④ SSE GET /job-status    │                            │                       │
       │──────────────────────────►│                            │ Phase A: DOWNLOADING  │
       │                           │  SseEmitter.send(...)      │ MinIO get oep-raw/... │
       │  event: progress          │◄──────────────────────────│                       │
       │  data: {pct:10,stage:..}  │                            │ Phase B: PROBING      │
       │◄──────────────────────────│                            │ FFprobe → metadata    │
       │  event: progress          │◄──────────────────────────│ { w,h,duration,fps }  │
       │  data: {pct:30,stage:..}  │                            │                       │
       │◄──────────────────────────│                            │ Phase C: TRANSCODING  │
       │  ...                      │                            │ FFmpeg → 4 renditions │
       │  event: progress          │◄──────────────────────────│ 360p/480p/720p/1080p  │
       │  data: {pct:85,stage:..}  │                            │                       │
       │◄──────────────────────────│                            │ Phase D: UPLOADING    │
       │  event: done              │◄──────────────────────────│ → MinIO oep-hls/      │
       │  data: {status:AVAILABLE} │                            │ → MinIO oep-thumb/    │
       │◄──────────────────────────│                            │                       │
       │                           │                            │ Phase E: FINALIZE     │
       │                           │                            │ UPDATE lesson_contents│
       │                           │                            │ status = AVAILABLE    │
       │                           │                            │ Xóa oep-raw object    │
       │                           │                            └───────────────────────┘
```

---

## 4. Phase 1 — Upload (core-lms-service)

### 4.1 Endpoints

#### `POST /api/contents/{lessonId}/video/presign`
**Giao thức:** REST/HTTP · **Auth:** JWT Bearer (role INSTRUCTOR)

**Request Body:**
```json
{
  "fileName": "bai-giang-chuong-1.mp4",
  "mimeType": "video/mp4",
  "fileSize": 524288000
}
```

**Validation:**
- `fileSize` ≤ 4GB
- `mimeType` ∈ `{ video/mp4, video/webm, video/quicktime, video/x-msvideo }`
- Giảng viên là OWNER hoặc CO_INSTRUCTOR của khóa học chứa lesson

**Response `200 OK`:**
```json
{
  "contentId": "550e8400-e29b-41d4-a716-446655440000",
  "presignedUrl": "http://localhost:9000/oep-raw/uploads/.../...?X-Amz-Signature=...",
  "objectKey": "uploads/{instructorId}/{contentId}/bai-giang-chuong-1.mp4",
  "expiresAt": "2026-10-18T16:00:00Z"
}
```

**Side effects:**
- INSERT `lesson_contents` với `upload_status = PENDING`
- Presigned URL TTL = 15 phút

---

#### `POST /api/contents/{contentId}/video/confirm`
**Giao thức:** REST/HTTP · **Auth:** JWT Bearer

**Request Body:**
```json
{
  "objectKey": "uploads/{instructorId}/{contentId}/bai-giang-chuong-1.mp4"
}
```

**Logic (Idempotent):**
```sql
UPDATE lesson_contents
SET upload_status = 'UPLOADED', file_size = ?, mime_type = ?
WHERE id = ? AND upload_status = 'PENDING'
```
Nếu đã `UPLOADED` rồi → trả 200 luôn, không publish lại event.

**Response `200 OK`:**
```json
{
  "contentId": "550e8400-...",
  "uploadStatus": "UPLOADED",
  "message": "Video đã được tiếp nhận. Đang xử lý..."
}
```

**Side effects:**
- INSERT `video_processing_jobs` (`status = QUEUED`)
- Publish `VideoUploadedEvent` lên RabbitMQ exchange `oep.events` routing key `video.uploaded`

---

#### `GET /api/contents/{contentId}/upload-progress`
**Giao thức:** **SSE (Server-Sent Events)** · **Auth:** JWT Bearer  
**Content-Type:** `text/event-stream`

**Spring Boot implementation:**
```java
// VideoContentController.java
@GetMapping(value = "/api/contents/{contentId}/upload-progress",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter getUploadProgress(@PathVariable UUID contentId) {
    SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30 phút timeout
    uploadProgressService.register(contentId, emitter);
    return emitter;
}
```

**SSE Event format:**
```
event: progress
data: {"jobId":"...","stage":"TRANSCODING","progressPct":45,"currentRendition":"720p"}

event: progress
data: {"jobId":"...","stage":"UPLOADING","progressPct":82,"currentRendition":"done"}

event: done
data: {"jobId":"...","status":"AVAILABLE","hlsMasterKey":"videos/.../master.m3u8"}

event: error
data: {"jobId":"...","status":"FAILED","message":"FFmpeg exit code 1"}
```

**Stage values:** `QUEUED` → `DOWNLOADING` → `PROBING` → `TRANSCODING` → `UPLOADING` → `DONE` / `FAILED`

---

### 4.2 Multipart Upload (file > 100MB)

#### `POST /api/contents/{lessonId}/video/multipart/init`
```json
// Response
{ "uploadId": "abc123", "objectKey": "uploads/..." }
```

#### `POST /api/contents/{lessonId}/video/multipart/presign-part`
```json
// Request
{ "uploadId": "abc123", "objectKey": "uploads/...", "partNumber": 1 }
// Response
{ "presignedUrl": "http://minio:9000/...?partNumber=1&uploadId=abc123&..." }
```

#### `POST /api/contents/{contentId}/video/multipart/complete`
```json
// Request
{
  "uploadId": "abc123",
  "objectKey": "uploads/...",
  "parts": [
    { "partNumber": 1, "etag": "\"abc\"" },
    { "partNumber": 2, "etag": "\"def\"" }
  ]
}
// Response → Giống /confirm
```

---

### 4.3 Cleanup Job (Scheduled)

```java
// core-lms-service — mỗi 15 phút
@Scheduled(cron = "0 */15 * * * *")
void cleanExpiredPendingUploads() {
    // Tìm lesson_contents WHERE upload_status = 'PENDING'
    //   AND created_at < now() - INTERVAL '20 minutes'
    // → SET upload_status = 'FAILED', processing_error = 'Upload timeout'
    // → Gọi MinIO deleteObject nếu object tồn tại (upload dở dang)
}
```

---

## 5. Phase 2 — Xử lý video (worker-service)

### 5.1 RabbitMQ — Cấu hình Queue

**Giao thức:** AMQP 0-9-1

```
Exchange: oep.events (topic, durable=true)
  │
  └─ routing key: video.uploaded
       └─ Queue: oep.worker.video-process
            ├─ durable = true
            ├─ x-message-ttl = 7200000  (2 giờ)
            └─ x-dead-letter-routing-key = oep.worker.video-process.dlq
```

```java
// worker-service: RabbitMqConfig.java
@Bean
Queue videoProcessQueue() {
    return QueueBuilder.durable(QUEUE_VIDEO_PROCESS)
        .withArgument("x-dead-letter-exchange", "")
        .withArgument("x-dead-letter-routing-key", QUEUE_VIDEO_PROCESS_DLQ)
        .withArgument("x-message-ttl", 7_200_000L)
        .build();
}

@Bean
Queue videoProcessDlq() {
    return QueueBuilder.durable(QUEUE_VIDEO_PROCESS_DLQ).build();
}
```

---

### 5.2 VideoProcessingListener

```java
@RabbitListener(queues = MessagingConstants.QUEUE_VIDEO_PROCESS)
public void handle(VideoUploadedEvent event, Message message, Channel channel)
        throws IOException {
    try {
        videoOrchestrationService.process(event);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    } catch (Exception e) {
        // Không requeue → gửi sang DLQ
        channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
    }
}
```

---

### 5.3 VideoOrchestrationService — Pipeline chi tiết

```
VideoUploadedEvent { contentId, jobId, rawObjectKey, mimeType, fileSize }
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  Phase A: DOWNLOADING                                    │
│  MinIO GET oep-raw/{rawObjectKey}                       │
│  → stream vào /tmp/oep/{jobId}/input.mp4               │
│  updateJob(DOWNLOADING, 5%)                             │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│  Phase B: PROBING (FFprobe)                              │
│  Đọc: width, height, duration, fps, codec               │
│  → Quyết định danh sách rendition cần tạo               │
│    (không upscale: chỉ tạo rendition ≤ resolution gốc) │
│  updateJob(PROBING, 10%)                                │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│  Phase C: TRANSCODING (FFmpeg)                          │
│                                                         │
│  FFmpeg command: multi-rendition HLS                    │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Input → split → 4 filter chains                  │  │
│  │   [v360] → scale=640:360 → libx264 500k          │  │
│  │   [v480] → scale=854:480 → libx264 1000k         │  │
│  │   [v720] → scale=1280:720 → libx264 2500k        │  │
│  │   [v1080] → scale=1920:1080 → libx264 5000k      │  │
│  │ Audio: AAC stereo (96/128/192 kbps theo rendition)│  │
│  │ HLS segment: 10 giây/segment, VOD playlist       │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
│  Parse FFmpeg stdout để theo dõi progress:              │
│    "time=00:01:23.45" → currentSec / totalSec * 80%    │
│    updateJob(TRANSCODING, 10%→90%, currentRendition)   │
│                                                         │
│  Output: /tmp/oep/{jobId}/                              │
│    360p/  playlist.m3u8 + 000.ts 001.ts ...             │
│    480p/  playlist.m3u8 + 000.ts 001.ts ...             │
│    720p/  playlist.m3u8 + 000.ts 001.ts ...             │
│    1080p/ playlist.m3u8 + 000.ts 001.ts ...             │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│  Phase C.1: Thumbnail & Sprite Sheet (song song)        │
│                                                         │
│  Thumbnail:                                             │
│    ffmpeg -ss {duration*0.1} -vframes 1                 │
│    -vf scale=1280:720 → thumb.jpg                       │
│                                                         │
│  Sprite Sheet (preview scrubbing):                      │
│    ffmpeg -vf "fps=1/{interval},scale=160:90,tile=5x4" │
│    -frames:v 1 → sprite.jpg                             │
│    (20 frame đặt đều → 1 ảnh ghép 5×4 = 800×360px)    │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│  Phase D: UPLOADING lên MinIO oep-hls                   │
│                                                         │
│  Object Key structure:                                  │
│    videos/{contentId}/master.m3u8                       │
│    videos/{contentId}/360p/playlist.m3u8                │
│    videos/{contentId}/360p/000.ts                       │
│    videos/{contentId}/360p/001.ts                       │
│    ...                                                  │
│    videos/{contentId}/thumb.jpg                         │
│    videos/{contentId}/sprite.jpg                        │
│                                                         │
│  Upload 4 rendition song song:                          │
│    CompletableFuture.allOf(r360, r480, r720, r1080)     │
│  Sau đó upload master.m3u8 + thumbnail                  │
│  updateJob(UPLOADING, 90%→98%)                          │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│  Phase E: FINALIZE                                      │
│                                                         │
│  UPDATE lesson_contents SET                             │
│    upload_status   = 'AVAILABLE',                       │
│    hls_master_key  = 'videos/{contentId}/master.m3u8',  │
│    thumbnail_key   = 'videos/{contentId}/thumb.jpg',    │
│    sprite_sheet_key= 'videos/{contentId}/sprite.jpg',   │
│    duration_seconds = {probed_duration}                 │
│                                                         │
│  UPDATE video_processing_jobs SET                       │
│    status = 'DONE', progress_pct = 100,                 │
│    finished_at = now()                                  │
│                                                         │
│  DELETE oep-raw/{rawObjectKey}  ← tiết kiệm storage    │
│                                                         │
│  Cleanup /tmp/oep/{jobId}/  ← xóa temp files           │
│                                                         │
│  SSE push event "done" → giảng viên nhận thông báo     │
│  Publish NotificationRequestedEvent → RabbitMQ          │
│    (email: "Video đã sẵn sàng phát")                   │
└─────────────────────────────────────────────────────────┘
```

### 5.4 Video Rendition Table

| Rendition | Resolution | Video Bitrate | Audio Bitrate | Codec | Segment | Target |
|-----------|------------|---------------|---------------|-------|---------|--------|
| `360p` | 640×360 | 500 kbps | 96 kbps AAC | H.264 baseline | 10s | Mobile 3G/4G |
| `480p` | 854×480 | 1000 kbps | 128 kbps AAC | H.264 main | 10s | Mobile WiFi / Tablet |
| `720p` | 1280×720 | 2500 kbps | 192 kbps AAC | H.264 high | 10s | Laptop / Desktop |
| `1080p` | 1920×1080 | 5000 kbps | 192 kbps AAC | H.264 high | 10s | Màn hình lớn |

**Quy tắc rendition:**
- **Không upscale:** video gốc 720p → chỉ tạo rendition 360p, 480p, 720p
- **Tối thiểu 1 rendition:** dù video gốc nhỏ cỡ nào
- **Scale giữ tỉ lệ:** `force_original_aspect_ratio=decrease` + padding letterbox

### 5.5 Master Playlist mẫu

```m3u8
#EXTM3U
#EXT-X-VERSION:3

#EXT-X-STREAM-INF:BANDWIDTH=596000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",NAME="360p"
360p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=1128000,RESOLUTION=854x480,CODECS="avc1.4d401e,mp4a.40.2",NAME="480p"
480p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=2692000,RESOLUTION=1280x720,CODECS="avc1.4d4028,mp4a.40.2",NAME="720p"
720p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=5192000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",NAME="1080p"
1080p/playlist.m3u8
```

Player (HLS.js) tự động parse → chọn rendition theo bandwidth → switch liền mạch.

### 5.6 Xử lý lỗi trong worker

| Tình huống | Hành vi |
|-----------|---------|
| Download MinIO thất bại | retry 3 lần → FAILED, message vào DLQ |
| FFmpeg exit code ≠ 0 | FAILED, ghi FFmpeg stderr vào `processing_error` |
| Upload MinIO thất bại | retry 3 lần → FAILED |
| Worker process crash | AMQP nack + requeue tự động (1 lần), sau đó DLQ |
| Disk full trong /tmp | IOException → FAILED |

---

## 6. Phase 3 — Streaming cho học viên

### `GET /api/contents/{contentId}/stream`

**Giao thức:** REST/HTTP → trả Presigned GET URL → HLS over HTTP  
**Auth:** JWT Bearer (role STUDENT, đã enroll)

**Validation:**
```
1. lesson_contents.upload_status == 'AVAILABLE'
2. enrollments: student đang ACTIVE trong khóa học chứa lesson
3. course.lifecycle_status IN ('PUBLISHED', 'ARCHIVED')
```

**Response `200 OK`:**
```json
{
  "contentId": "550e8400-...",
  "streamUrl": "http://localhost:9000/oep-hls/videos/{contentId}/master.m3u8?X-Amz-Expires=3600&...",
  "thumbnailUrl": "http://localhost:9000/oep-thumbnails/videos/{contentId}/thumb.jpg",
  "spriteSheetUrl": "http://localhost:9000/oep-thumbnails/videos/{contentId}/sprite.jpg",
  "durationSeconds": 3600,
  "expiresAt": "2026-10-18T17:00:00Z"
}
```

**Presigned URL TTL:** 1 giờ (học viên refetch khi hết hạn)

**Phía Frontend:**
```javascript
// HLS.js tự xử lý Adaptive Bitrate
const hls = new Hls();
hls.loadSource(streamUrl);          // master.m3u8
hls.attachMedia(videoElement);
// Player tự động chọn 360p/480p/720p/1080p theo băng thông
```

---

## 7. Cấu trúc MinIO Buckets

```
MinIO :9000
│
├── oep-raw/          (Private — chỉ worker đọc, xóa sau khi transcode)
│   └── uploads/{instructorId}/{contentId}/{filename}.mp4
│
├── oep-hls/          (Public-read hoặc Presigned URL)
│   └── videos/{contentId}/
│       ├── master.m3u8
│       ├── 360p/
│       │   ├── playlist.m3u8
│       │   ├── 000.ts
│       │   └── 001.ts ...
│       ├── 480p/  ...
│       ├── 720p/  ...
│       └── 1080p/ ...
│
└── oep-thumbnails/   (Public-read)
    └── videos/{contentId}/
        ├── thumb.jpg      (Thumbnail chính — 1280×720)
        └── sprite.jpg     (Sprite sheet 20 frame — 800×360)
```

---

## 8. Database Schema bổ sung

### 8.1 Bổ sung cột cho `lesson_contents`

```sql
ALTER TABLE lesson_contents ADD COLUMN hls_master_key    VARCHAR(500);
ALTER TABLE lesson_contents ADD COLUMN thumbnail_key     VARCHAR(500);
ALTER TABLE lesson_contents ADD COLUMN sprite_sheet_key  VARCHAR(500);
ALTER TABLE lesson_contents ADD COLUMN processing_error  TEXT;
```

_(Trong dev dùng `ddl-auto: update` — Hibernate tự thêm cột)_

### 8.2 Bảng mới `video_processing_jobs`

```sql
CREATE TABLE video_processing_jobs (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    content_id       UUID        NOT NULL REFERENCES lesson_contents(id) ON DELETE CASCADE,
    status           VARCHAR(40) NOT NULL DEFAULT 'QUEUED',
    progress_pct     INT         NOT NULL DEFAULT 0,
    renditions_done  JSONB,                    -- ["360p","480p"] đã hoàn thành
    raw_object_key   VARCHAR(500),
    error_message    TEXT,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vpj_content_id ON video_processing_jobs(content_id);
CREATE INDEX idx_vpj_status ON video_processing_jobs(status);
```

**`status` lifecycle:**
```
QUEUED → DOWNLOADING → PROBING → TRANSCODING → UPLOADING → DONE
                                                          → FAILED
```

### 8.3 Enum mới trong `core-model`

```java
// VideoJobStatus.java
public enum VideoJobStatus {
    QUEUED, DOWNLOADING, PROBING, TRANSCODING, UPLOADING, DONE, FAILED
}
```

---

## 9. Cấu trúc thư mục code

```
core-model/
├── entity/course/
│   └── VideoProcessingJob.java         ← THÊM MỚI
├── enums/
│   └── VideoJobStatus.java             ← THÊM MỚI
├── event/
│   └── VideoUploadedEvent.java         ← THÊM MỚI
└── constant/
    └── MessagingConstants.java         ← BỔ SUNG 3 hằng số

core-lms-service/.../corelms/
├── config/
│   └── MinioConfig.java                ← THÊM MỚI
├── service/
│   ├── media/
│   │   ├── MediaStorageService.java    ← interface
│   │   └── MinioMediaStorageService.java
│   ├── content/
│   │   └── VideoContentService.java    ← initUpload, confirmUpload, getStreamUrl
│   └── progress/
│       └── UploadProgressService.java  ← quản lý SseEmitter registry
├── controller/
│   └── VideoContentController.java     ← 7 endpoints
├── dto/
│   ├── request/
│   │   ├── InitUploadRequest.java
│   │   ├── ConfirmUploadRequest.java
│   │   └── CompleteMultipartRequest.java
│   └── response/
│       ├── PresignedUploadResponse.java
│       ├── UploadProgressResponse.java
│       └── StreamUrlResponse.java
└── scheduler/
    └── UploadCleanupScheduler.java     ← @Scheduled cleanup PENDING

worker-service/.../worker/
├── config/
│   ├── MinioConfig.java
│   ├── RabbitMqConfig.java             ← video queue + DLQ
│   └── FfmpegConfig.java               ← paths + tempDir
├── listener/
│   └── VideoProcessingListener.java
├── service/
│   ├── VideoOrchestrationService.java  ← điều phối toàn bộ pipeline
│   ├── MinioRawStorageService.java     ← download + delete oep-raw
│   ├── VideoProbeService.java          ← FFprobe JSON parse
│   ├── VideoTranscodingService.java    ← FFmpeg multi-rendition
│   ├── ThumbnailService.java           ← thumb + sprite sheet
│   ├── HlsUploadService.java           ← upload song song lên oep-hls
│   └── JobStatusService.java           ← cập nhật video_processing_jobs + SSE notify
└── model/
    └── VideoMetadata.java              ← { width, height, duration, fps, codec }
```

---

## 10. Docker Compose — MinIO

Thêm vào `docker-compose.yml`:

```yaml
  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: oep
      MINIO_ROOT_PASSWORD: oep_secret
    ports:
      - "9000:9000"    # S3 API
      - "9001:9001"    # Web Console  →  http://localhost:9001
    volumes:
      - miniodata:/data

  minio-init:
    image: minio/mc:latest
    depends_on:
      minio:
        condition: service_started
    entrypoint: >
      /bin/sh -c "
        sleep 3 &&
        mc alias set local http://minio:9000 oep oep_secret &&
        mc mb --ignore-existing local/oep-raw &&
        mc mb --ignore-existing local/oep-hls &&
        mc mb --ignore-existing local/oep-thumbnails &&
        mc anonymous set download local/oep-hls &&
        mc anonymous set download local/oep-thumbnails &&
        echo 'MinIO buckets initialized'
      "

volumes:
  miniodata:
```

**MinIO Console:** http://localhost:9001 (user: `oep`, pass: `oep_secret`)

---

## 11. Dependencies POM

### Parent `pom.xml` (thêm version management):
```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.10</version>
</dependency>
```

### `core-lms-service/pom.xml`:
```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
</dependency>
```

### `worker-service/pom.xml`:
```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
</dependency>
<!-- Jackson đã có qua Spring Boot starter -->
```

### `application.yml` cho worker-service:
```yaml
oep:
  minio:
    endpoint: http://localhost:9000
    access-key: oep
    secret-key: oep_secret
    bucket-raw: oep-raw
    bucket-hls: oep-hls
    bucket-thumbnails: oep-thumbnails
  ffmpeg:
    binary: /usr/bin/ffmpeg
    ffprobe: /usr/bin/ffprobe
    temp-dir: /tmp/oep
```

> **Yêu cầu:** `sudo apt install ffmpeg` trên máy chạy worker-service.

---

## 12. Thứ tự triển khai

```
Sprint 1 — Foundation (1-2 ngày)
  [1] docker-compose.yml: thêm minio + minio-init
  [2] core-model: VideoJobStatus enum
  [3] core-model: VideoUploadedEvent record
  [4] core-model: MessagingConstants (3 hằng số mới)
  [5] core-model: VideoProcessingJob entity
  [6] core-model: Bổ sung 4 cột vào LessonContent
  [7] mvn -pl core-model install

Sprint 2 — Upload API (2-3 ngày)
  [8]  MinioConfig (core-lms-service)
  [9]  MediaStorageService interface + MinioMediaStorageService impl
  [10] VideoContentService: initUpload() + confirmUpload()
  [11] UploadProgressService: SseEmitter registry (ConcurrentHashMap)
  [12] VideoContentController: 7 endpoints
  [13] UploadCleanupScheduler (@Scheduled)
  [14] Test: curl upload flow đơn giản

Sprint 3 — Transcoding Pipeline (3-4 ngày)
  [15] MinioConfig + FfmpegConfig (worker-service)
  [16] RabbitMqConfig: khai báo queue video + DLQ
  [17] MinioRawStorageService: download + delete
  [18] VideoProbeService: FFprobe subprocess + parse JSON
  [19] VideoTranscodingService: FFmpeg multi-rendition + progress parse
  [20] ThumbnailService: thumb + sprite sheet
  [21] HlsUploadService: parallel upload CompletableFuture
  [22] JobStatusService: UPDATE DB + push SSE event
  [23] VideoOrchestrationService: điều phối 5 phase
  [24] VideoProcessingListener: @RabbitListener + manual ack
  [25] Test: upload → confirm → xem log worker transcode

Sprint 4 — Stream & End-to-End (1-2 ngày)
  [26] getStreamUrl endpoint (validate enrollment)
  [27] Test full flow: upload → transcode → stream với HLS.js
  [28] Test DLQ: giả lập FFmpeg lỗi
  [29] Test SSE: xem progress real-time trên browser
  [30] Test adaptive bitrate: throttle Chrome DevTools
```

---

## 13. Production Roadmap

| Hạng mục | Dev (MinIO) | Production |
|----------|-------------|-----------|
| **Object Storage** | MinIO single node | GCS Multi-region / AWS S3 |
| **Transcoding** | FFmpeg subprocess local | GCP Transcoder API / AWS MediaConvert |
| **CDN** | MinIO Presigned URL trực tiếp | CloudFront / Cloud CDN Edge |
| **Video Codec** | H.264 only | H.264 + AV1 (Chrome/Android mới) |
| **DRM** | Không | Widevine + FairPlay (nếu có yêu cầu) |
| **Worker Scale** | 1 instance :8083 | N instances auto-scale (K8s HPA) |
| **Progress** | SSE từ core-lms | SSE + Redis pub/sub (N instance) |
| **Storage swap** | `MinioMediaStorageService` | `GcsMediaStorageService` (swap interface) |

> **Thiết kế interface `MediaStorageService`** đảm bảo swap provider storage **không cần sửa bất kỳ business logic nào** — chỉ đổi implementation class và config.
