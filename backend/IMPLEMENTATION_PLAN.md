# M2 implementation plan

This file tracks code implementation in the repository. A design/API contract is not counted as implemented until its backend behavior exists. Automated tests and runtime verification are tracked separately.

## Progress

| Work item | Status | Notes |
| --- | --- | --- |
| M2.1 Course, module and lesson CRUD | Implemented; API smoke verified | Create/read/list/update paths for courses, modules and lessons passed against local PostgreSQL on 2026-10-22 and are now covered by the repeatable M2.8 gateway smoke script. See scope and limits below. |
| M2.2 Reordering modules and lessons | Implemented | Complete-permutation APIs, parent-row locks, collision-safe two-phase position updates, and unique parent-position constraints added. Seven service unit tests pass; DB concurrency/API integration checks remain outstanding. |
| M2.3 Lesson content CRUD | Implemented | TEXT/LINK list/create/read/update/delete and file metadata creation are available to OWNER/CO_INSTRUCTOR. Six service unit tests and gateway API smoke checks against local PostgreSQL passed; M2.6 now completes file upload, access and storage deletion. |
| M2.4 Course lifecycle and instructor management | Implemented; isolated and local gateway smoke passed; local V2 applied | Owner publish/unpublish/archive/restore and ADMIN disable/enable endpoints added; instructor list/add/remove added. Publish uses the OpenAPI's provisional readiness rule (at least one lesson and one AVAILABLE content item). Nine M2.4 unit tests pass. The full gateway flow passed against an isolated PostgreSQL database and the local `oep` database on 2026-10-24. V2 was applied to the local `oep` primary on 2026-10-24; its audit constraint accepted lifecycle writes during the gateway smoke. Student instructor-list reads are covered provisionally by M2.5; ownership transfer remains out of scope. |
| M2.5 Outline and enrollment-aware student access | Implemented provisionally; policy alignment pending | Anonymous PUBLIC + PUBLISHED outline previews and course-manager reads work in all states. Outline lesson loading now uses one bulk query, removing per-module N+1 reads. Student course details, PRIVATE outlines, instructor lists, module/lesson reads and AVAILABLE content reads require an ACTIVE enrollment in a PUBLISHED course. COMPLETED access and final M3 entitlement ownership/failure semantics remain open. |
| M2.6 File storage and signed URLs | Implemented; unit and gateway/PostgreSQL/storage smoke verified (commit `f7abc72`) | Server-managed opaque object keys, S3-compatible storage, 100 MiB/MIME policy, 10-minute PUT and 5-minute GET URLs, and metadata verification before availability. RustFS is used for local development; AWS S3 SDK adapter supports S3-compatible deployment. |
| M2.7 Cross-module deletion/reference checks | Empty module and guarded lesson deletion implemented; physical course deletion remains deferred | Empty modules return 409 `MODULE_NOT_EMPTY`; lesson deletion locks the row and returns 409 `LESSON_NOT_EMPTY` if content, completion rules, progress, quizzes, assignments, or AI references exist. No dependent rows are cascaded. DRAFT-course DELETE remains an archive alias for OWNER only. |
| M2.8 Integration tests, API contract review and deployment | Gateway smoke passed on isolated and local `oep` PostgreSQL; production migration rollout remains open | Repeatable gateway/PostgreSQL/RustFS smoke script and OpenAPI inventory/consistency tests added. Manual M2 V2 SQL is checked in and verified against isolated and local PostgreSQL databases; Flyway remains disabled. Empty-module and guarded lesson deletion are implemented; content reordering and production migration rollout remain outstanding. |

## M2.1 delivered scope

- `POST /api/v1/courses`: create a DRAFT course and its OWNER association in one transaction.
- `GET /api/v1/courses`: paginated PUBLIC + PUBLISHED catalog with optional title search.
- `GET /api/v1/courses/{courseId}`: public published course metadata, or management read for a course manager.
- `GET /api/v1/my/courses`: paginated courses where the caller is an OWNER or CO_INSTRUCTOR.
- `PATCH /api/v1/courses/{courseId}`: title, description, learning mode and start/end dates. Course lifecycle cannot be changed through PATCH.
- Module list/create/read/update and lesson list/create/read/update endpoints under `/api/v1/courses/...`.
- Course/module/lesson ownership checks, parent-chain validation, paging limits, DTO validation, default values and basic date/position/default-module checks.
- Gateway route and public GET access for the public course catalog and public course metadata.

Capacity changes and PUBLIC/PRIVATE visibility changes are rejected by course PATCH until M3 defines occupied-seat semantics and invite-code/enrollment behavior. Module/lesson read routes now enforce the provisional M2.5 ACTIVE-enrollment policy. Delete and reorder endpoints are not included in M2.1. Course creation currently treats `User.role = INSTRUCTOR` as the approved instructor signal; confirm this against the instructor-approval flow before production use.

## M2.2 delivered scope

- `PATCH /api/v1/courses/{courseId}/module-order` reorders every module in a course using `ordered_ids`.
- `PATCH /api/v1/courses/{courseId}/modules/{moduleId}/lesson-order` reorders every lesson in a module using `ordered_ids`.
- Both operations reject missing, duplicate, or out-of-parent IDs and run in one transaction.
- Reorder locks the parent row (`courses` for modules, `course_modules` for lessons), first moves children into distinct temporary negative positions, flushes, then writes contiguous zero-based positions and flushes again. The corresponding create operation locks the same parent to serialize position allocation with reorder.
- Added database unique constraints for `(course_id, position)` on modules and `(module_id, position)` on lessons.
- `mvn -pl core-lms-service -am test` succeeded: seven service unit tests cover valid module/lesson reorder, contiguous resulting positions, omitted/duplicate/foreign IDs, and missing course-instructor association. Database concurrency/API integration checks remain outstanding.

## M2.3 delivered scope

- Added paginated content list (optional upload-status filter), create, read, update and delete endpoints under each lesson.
- This slice introduced TEXT and LINK mutations and file metadata creation. TEXT requires `text_content`; LINK requires an absolute HTTP(S) `external_url`. TEXT/LINK become `AVAILABLE` immediately; file records remain `PENDING` until M2.6 verifies the uploaded object.
- Only OWNER/CO_INSTRUCTOR can read or mutate these endpoints. Student entitlement-based reads remain in M2.5.
- Content responses omit `object_key`. Create appends after the current maximum position by default, serializes allocation with a lesson-row lock, and the entity has a unique `(lesson_id, position)` constraint.
- Deletion locks the content row and returns 409 when lesson progress, AI chunks or content change history reference it.
- `mvn -pl core-lms-service -am test` succeeded: six content-management unit tests plus the seven M2.2 tests (13 total).
- Gateway smoke checks against Core LMS and local PostgreSQL passed: TEXT creation returned 201/AVAILABLE; list, read and update returned 200; invalid `javascript:` LINK returned 422; delete returned 200 and a subsequent read returned 404. Responses omitted `object_key`. Temporary test users and courses were removed afterward.

## M2.4 delivered scope

- Added OWNER-only `POST /courses/{courseId}/publish`, `unpublish`, `archive` and `restore`, with locked course rows and explicit transition validation.
- Publishing currently follows the provisional OpenAPI readiness rule: the course must contain at least one lesson and at least one `AVAILABLE` content item. TEXT/LINK items created through M2.3 meet the content condition because they are immediately AVAILABLE.
- Added ADMIN-only `disable` and `enable`; enabling returns the course to DRAFT for owner review.
- Added OWNER-only co-instructor add/remove and manager-only instructor listing. Added instructors must be active system-level INSTRUCTOR users; the course OWNER cannot be removed. Course-row locking serializes instructor membership changes.
- Student access to the instructor list now requires an ACTIVE enrollment under the provisional M2.5 policy; final M3 entitlement ownership remains pending. Ownership transfer is not part of the current API contract.
- `CourseLifecycleAndInstructorManagementTest` covers readiness, transitions, role restrictions, instructor assignment, and owner-removal protection. All nine M2.4 unit tests pass.
- `mvn -pl core-lms-service -am test` succeeded on 2026-10-23: all 22 Core LMS service tests passed (9 M2.4, 7 M2.2, 6 M2.3).
- Direct Core LMS API smoke checks against the project's PostgreSQL primary/replica passed on 2026-10-23: empty-course publish returned 409 `COURSE_NOT_READY`; publish/unpublish/republish, archive/restore, ADMIN disable/enable and co-instructor add/list/remove passed; co-instructor archive and instructor disable returned 403.
- Smoke-test course and accounts were removed and verified absent afterward. Core LMS and Docker dependencies were stopped after the check. Gateway-level smoke testing remains outstanding.

## M2.5 delivered scope (provisional)

- Added `GET /api/v1/courses/{courseId}/outline` returning ordered module and lesson metadata without content or file fields.
- Anonymous users can preview outlines only for `PUBLIC + PUBLISHED` courses. OWNER/CO_INSTRUCTOR/ADMIN can inspect outlines for courses they manage, including DRAFT, PRIVATE, ARCHIVED and DISABLED courses.
- Student reads of non-preview course details, outlines, instructor lists, modules, lessons and lesson contents use the local enrollment table and require an ACTIVE enrollment in a PUBLISHED course. Student content lists/details include only `AVAILABLE` items. COMPLETED remains denied provisionally.
- Read authorization runs against the primary database so replication lag cannot preserve stale ACTIVE enrollment or PUBLISHED state. Public catalog reads remain eligible for the read replica; course details, outlines and learning reads use the primary for authorization.
- Final COMPLETED policy, M3 entitlement service ownership, and dependency-failure mapping remain pending. PRIVATE enrollment still relies on L3 completing its invite-code flow before creating the enrollment row.

## M2.6 delivered scope

- File content metadata is created in `PENDING`; allowed content types are VIDEO, PDF, SLIDE, DOCUMENT and FILE with MIME allow-lists. Maximum declared and uploaded size is 100 MiB.
- The server generates an opaque random object key and never returns it in API responses. OWNER/CO_INSTRUCTOR can request a presigned PUT URL (10-minute default) and must send the declared `Content-Type`.
- `POST /api/v1/contents/{contentId}/complete-upload` has no request body. The service checks storage object existence, size and `Content-Type`, deletes invalid uploads, and only then marks content `AVAILABLE`.
- OWNER/CO_INSTRUCTOR/ADMIN and provisionally ACTIVE students of PUBLISHED courses can request a presigned GET URL (5-minute default) for AVAILABLE files. Student access follows the M2.5 local-enrollment policy. Existing signed URLs may remain valid until expiry.
- File deletion removes the stored object before removing metadata. Storage errors fail the operation rather than silently leaving a successful metadata deletion.
- AWS SDK v2 backs the S3-compatible `ObjectStorageService`; local Docker Compose uses RustFS. Storage endpoint, bucket, credentials and URL TTLs are environment-configurable. Production deployments must supply their own credentials and storage endpoint.
- `mvn -pl core-lms-service -am test` passed on 2026-10-23: 35 tests, no failures/errors. Gateway API smoke testing with PostgreSQL and RustFS verified metadata creation, oversize rejection, direct PUT, pending-download denial, HEAD verification, publishing, student entitlement checks and byte-identical signed GET download. Smoke-test users, courses and objects were removed afterward.
- Current limits: uploads use a single presigned PUT (no multipart/resumable transfer); size is authoritatively checked after upload and oversized objects are deleted at completion. Content type is checked from storage metadata; magic-byte inspection, malware scanning and immediate revocation of issued URLs are not implemented.

## M2.8 delivered scope (M2.1-M2.6)

- Added `backend/scripts/m2-integration-smoke.ps1`, a repeatable gateway API smoke flow using local PostgreSQL and RustFS. It covers course/module/lesson create/read/update, module and lesson reordering, TEXT/LINK content operations, signed file upload and download, publication, public outline/catalog reads and ACTIVE-enrollment access.
- The script creates uniquely named temporary accounts and course data, deletes content through the API (including stored files), removes dependent smoke rows, and leaves Docker volumes intact. It expects the local services to be started using the instructions in `backend/README.md`.
- Added an OpenAPI contract test that rejects duplicate YAML keys, requires unique operation IDs and response definitions, and checks the documented M2 operations against the implemented/planned inventory. It also protects the server-managed object-key and bodyless upload-completion contract.
- Contract review found several operations without matching controller handlers. Empty-module deletion, guarded deletion of empty lessons, and the deprecated DRAFT-course archive alias are implemented and reflected in the OpenAPI inventory. Content reordering remains outside the implemented M2.1-M2.6 scope.
- Prior verification (2026-10-24): `mvn -pl core-lms-service -am test` passed 49 tests with no failures/errors. The gateway/PostgreSQL/RustFS smoke script passed the full M2.1-M2.6 flow against a temporary isolated PostgreSQL database, including uploaded/downloaded byte comparison and cleanup verification. V2 was applied twice against a separate PostgreSQL database and its table/index/check behavior passed. At that point the existing `oep` database was not modified; it was subsequently updated as recorded below.
- Additional verification on 2026-10-24: `mvn -pl core-lms-service -am clean verify` passed 50 tests and enforced 78.80% M2 service line coverage. The optimized public outline returned 10 modules / 100 lessons; 100 direct Core LMS requests measured p95 123.81 ms after 10 warm-up requests. The benchmark database was temporary and removed afterward.
- Lesson-delete verification on 2026-10-24: `mvn -pl core-lms-service -am clean verify` passed 52 tests and enforced 78.99% M2 service line coverage (440/557 lines). The gateway/PostgreSQL/RustFS smoke passed on a disposable database, including deleting an empty lesson (200, then 404) and refusing deletion of a lesson with content (409). The first smoke attempt exposed a stale `core-model` JAR in the local Maven repository; after installing the updated module and restarting the services, the full smoke passed. The temporary database was dropped; the existing `oep` database was untouched.
- This closes repeatable integration and API contract checks for M2.1-M2.6 only. Production deployment is not complete: schema migrations, production secret management and provider-specific storage configuration remain open.

## Implementation notes

- Existing JPA entities are in `core-model`; this slice adds Core LMS repositories, DTOs, service and controller.
- Schema generation remains the existing development `ddl-auto: update` setup. Manual M2 V2 SQL lives under `core-model/src/main/resources/db/migration`; it is not auto-applied. Production migration baseline and rollout remain open.
- Core catalog search uses a non-null normalized search term to avoid PostgreSQL inferring a null parameter as `bytea`.
- Create and update operations flush before building their response so generated `created_at` and `updated_at` values are included.

## Verification (2026-10-22)

- `mvn install -DskipTests` succeeded for all backend modules (`core-model`, `api-gateway`, `core-lms-service`, `assessment-service`, and `worker-service`). Automated tests were skipped and no new automated tests were added.
- Manual API smoke checks passed for login, public catalog listing, and course/module/lesson create, read, list, and update operations against the local PostgreSQL containers.
- The public catalog returned an empty list while the newly created course was `DRAFT`; this is expected because the catalog only includes `PUBLIC` + `PUBLISHED` courses.
- Vietnamese request data was confirmed stored as UTF-8 in PostgreSQL. Windows PowerShell 5.1 displayed some JSON response text with mojibake; this was a client display/decoding issue.
- M2.3 local gateway/PostgreSQL API smoke checks passed on 2026-10-22 after installing the updated `core-model` artifact. The process used `TZ=UTC` because PostgreSQL rejected the host-provided `Asia/Saigon` session timezone.

## Leader checklist — L2 / M2 Course

Checklist copied from the team plan. Checkboxes below preserve the completion state supplied with that plan; update them as work is completed and reflected in the team sheet. This checklist tracks the assigned scope and acceptance criteria, while the implementation progress above tracks the current repository state.

### Ngày 1–3 (3 ngày) — OpenAPI và rà ERD Module 2 (BE)

- [x] Viết hợp đồng OpenAPI cho course, module, lesson, content, upload, access-url; rà ERD 4 tầng `Course → Module → Lesson → Content` và ma trận quyền Owner / Co-Instructor / Student.
- [x] Thống nhất với L3 interface kiểm tra enrollment ACTIVE (`EnrollmentChecker`) và với L5 danh mục sự kiện phát ra. Tạm chốt trong tài liệu M2 theo `EnrollmentChecker.requireActive(studentId, courseId)` và các event `COURSE_CONTENT_UPDATED` / `COURSE_DISABLED` ở `docs/m2-course.md`; xác nhận liên nhóm có thể bổ sung sau.
- [x] BE: `openapi/m2-course.yaml` được merge, có ít nhất 20 endpoint, mỗi endpoint có ví dụ request / response.
- [x] Tài liệu: ma trận quyền Module 2 (3 vai trò × các thao tác) được duyệt. Ma trận OWNER / CO_INSTRUCTOR / STUDENT đã chốt trong tài liệu M2 tại `docs/m2-course.md`; xác nhận chính thức với nhóm có thể bổ sung sau.
- [x] Ngoại lệ: ghi trong OpenAPI danh sách mã lỗi của module, gồm 403 Co-Instructor xóa khóa học, 409 đổi trạng thái vòng đời không hợp lệ, 413 / 415 khi upload.
- [x] Phi chức năng: quy ước phân trang (mặc định 20, tối đa 100) và định dạng lỗi thống nhất với common. Tạm chốt cho M2 theo `page=1`, `page_size=20` tối đa 100 và error envelope trong OpenAPI; đối chiếu với common/M3-M5 có thể bổ sung sau.

### Ngày 4–8 (5 ngày) — Entity và CRUD khóa học, chương, bài, nội dung TEXT / LINK (BE)

- [x] Tạo entity, repository, migration V2 cho 6 bảng Module 2; CRUD course, `course_modules` (có module mặc định), lessons, `lesson_contents` loại TEXT / LINK; sắp xếp theo `position`; chuyển trạng thái DRAFT → PUBLISHED → ARCHIVED.
  - [x] Entity/repository, create/list/read/update handlers và lifecycle đã có.
  - [x] Tạo module mặc định khi tạo course; sắp xếp module/lesson/content theo `position`.
  - [x] Migration V2 thủ công cho sáu bảng M2, các index/unique rule và check constraint audit action; giữ nguyên quy ước V3, không bật Flyway.
  - [x] Xóa lesson rỗng; trả 409 `LESSON_NOT_EMPTY` khi có content, completion rule, progress, quiz, assignment hoặc tham chiếu AI; không cascade dữ liệu liên quan. Course dùng archive thay cho xóa vật lý; module chỉ xóa khi rỗng.
  - `CourseCreationTest` covers default-module creation and rejection of non-instructors. `mvn -pl core-lms-service -am clean verify` passed on 2026-10-24: 50 tests, no failures/errors. V2 ran twice successfully against an isolated PostgreSQL database with only the prerequisite `users` and legacy `audit_logs` tables; it created all six tables/indexes, accepted `PUBLISH_COURSE`, rejected an invalid action and rejected a second OWNER. V2 was later applied to the local `oep` primary on 2026-10-24 after checking for conflicting positions, multiple default modules/owners, and unsupported audit actions; none were found. The M2 gateway smoke flow passed against `oep`, including course lifecycle actions, and cleaned up its temporary data.
- [x] Phân quyền Owner / Co-Instructor trong từng thao tác và ghi audit khi đổi trạng thái vòng đời. Lifecycle audit được ghi cùng transaction với thay đổi trạng thái.
- [x] BE: API CRUD đầy đủ 4 tầng, ít nhất 25 test, coverage service ≥ 70%.
  - [x] Có create/list/read/update và guarded delete lesson cho bốn tầng; 52 test Core LMS đang pass.
  - [x] Lesson deletion được đánh dấu implemented trong OpenAPI; empty lesson xóa được, lesson có tham chiếu trả 409.
  - [x] Coverage dòng các service thuộc M2 đạt 78.99% (JaCoCo: 440/557 dòng; `CourseManagementService`, `LessonContentStorageService`, `FileUploadPolicy`, `TextContentSanitizer`). `mvn -pl core-lms-service -am clean verify` tạo report mới và chặn build nếu thấp hơn 70%.
- [ ] Ngoại lệ: Co-Instructor xóa khóa học hoặc đổi chủ trả 403; xuất bản khóa không có bài học trả 409; `position` trùng được chuẩn hóa tự động.
  - [x] Xuất bản course chưa sẵn sàng trả 409.
  - [x] Co-Instructor gọi DELETE DRAFT-course archive alias nhận 403.
  - [x] Chèn module/lesson/content tại `position` đã dùng tự dời các mục sau, giữ thứ tự.
  - [ ] Transfer ownership API chưa có trong hợp đồng; để lại chờ chốt.
- [x] Tối ưu: lấy cây khóa học bằng tối đa ba truy vấn, không N+1; kiểm thử 10 chương / 100 bài đạt p95 ≤ 300 ms.
  - Public outline dùng ba truy vấn dữ liệu (course + bulk lessons + modules). Unit test xác nhận một lần bulk lookup cho 10 × 10 bài. PostgreSQL + Core LMS local: 100 request sau 10 warm-up, median 100.54 ms, p95 123.81 ms, max 187.41 ms.
- [x] Bảo mật: `text_content` được làm sạch HTML (sanitize) để chống XSS khi ghi và khi trả nội dung, gồm cả dữ liệu cũ.
  - Verification (2026-10-24): `mvn -pl core-lms-service -am clean verify` passed: 52 tests, no failures/errors; JaCoCo M2 service line coverage is 78.99% (440/557). The OpenAPI contract test passed. The gateway smoke flow passed against temporary PostgreSQL + RustFS, including empty-lesson deletion (200/404), referenced-lesson rejection (409), signed upload/download, enrollment gating, lifecycle transitions, and cleanup. V2 SQL was also applied to the local `oep` primary after checking for conflicting positions, multiple default modules/owners, and unsupported audit actions; none were found. On `oep`, all required indexes were present and the full gateway smoke flow passed, including publish, unpublish, republish, archive, restore, and cleanup. A first run stopped at the public catalog read because the replica was down; after starting the local replica, the rerun passed.

### Ngày 9–15 (7 ngày) — Upload media qua S3 presigned URL (BE)

- [ ] Làm API cấp presigned PUT (giới hạn MIME và dung lượng: video ≤ 2 GB, PDF / slide ≤ 50 MB), API xác nhận upload (PENDING → UPLOADED → AVAILABLE, FAILED); lưu `object_key`, `file_name`, `mime_type`, `file_size`, `duration_seconds`.
- [ ] Hỗ trợ multipart upload (mỗi phần 10 MB) cho video lớn; job dọn file PENDING quá 24 giờ.
- [ ] BE: `/contents/{id}/upload-url` và `/confirm-upload` chạy với LocalStack, ít nhất 12 test.
- [ ] Ngoại lệ: MIME không hợp lệ trả 415, quá dung lượng trả 413, presigned URL hết hạn sau 15 phút, upload dở dang được job dọn sau 24 giờ, xác nhận khi file chưa có trên S3 trả 409.
- [x] Bảo mật: không lưu file trong DB; presigned URL chỉ cho đúng 1 `object_key` và đúng MIME; `object_key` sinh ngẫu nhiên (UUID).
- [ ] Tối ưu: upload video 500 MB hoàn tất ở chế độ multipart với 3 phần song song.

### Ngày 9–15 (7 ngày) — Course builder cho giảng viên (FE)

- [ ] Làm màn hình tạo khóa học, chương, bài; kéo thả đổi thứ tự; upload tài liệu có thanh tiến trình; chuyển trạng thái xuất bản.
- [ ] Co-Instructor không thấy nút xóa khóa học và đổi chủ.
- [ ] FE: 5 màn hình theo Figma (danh sách khóa, builder, soạn nội dung, upload, xuất bản).
- [ ] Ngoại lệ: upload thất bại cho phép thử lại, hủy upload giữa chừng; mất kết nối khi lưu thứ tự thì giữ nháp và báo lỗi.
- [ ] Tối ưu: lưu thứ tự theo lô (1 request cho mỗi lần kéo thả xong), nháp tự lưu mỗi 30 giây.
- [ ] Test: ít nhất 8 test component và 1 E2E (tạo khóa → thêm bài → upload PDF → xuất bản).

### Ngày 16–22 (7 ngày) — CloudFront signed URL và kiểm soát truy cập nội dung (BE)

- [ ] Student chỉ nhận signed URL ngắn hạn khi enrollment ACTIVE và khóa PUBLISHED / ARCHIVED; không bao giờ trả `object_key` hay URL S3 gốc.
- [ ] Cấu hình CloudFront với OAC, S3 chặn truy cập công khai; viết test phân quyền.
- [ ] BE: `/lessons/{id}/contents/{cid}/access-url` chạy được, ít nhất 12 test phân quyền.
- [ ] Ngoại lệ: chưa ghi danh hoặc bị REMOVED trả 403, khóa DISABLED trả 404, file chưa AVAILABLE trả 409; URL hết hạn để FE xin lại.
- [ ] API Gateway: CloudFront phân phối nội dung; S3 chỉ cho phép truy cập qua OAC; 100% request trực tiếp tới S3 bị từ chối.
- [ ] Tối ưu: TTL signed URL 10 phút; cache-control cho PDF / slide, cache hit rate CloudFront ≥ 80% trong thử nghiệm.

### Ngày 16–22 (7 ngày) — Lesson viewer: video, PDF, text, link (FE)

- [ ] Làm trình xem theo `content_type` (video, PDF, text, link), tab nội dung và cây khóa học bên cạnh; tự xin lại signed URL khi hết hạn.
- [ ] Cung cấp điểm gắn hook `useLessonProgress` cho L3 và hook hiển thị nút AI Tutor cho L1.
- [ ] FE: trang học xem được 4 loại nội dung theo Figma, dùng được ở màn hình mobile 360 px.
- [ ] Ngoại lệ: URL hết hạn tự làm mới 1 lần rồi mới báo lỗi; file đang xử lý hiển thị trạng thái chờ; mất mạng khi stream hiển thị thông báo và nút thử lại.
- [ ] Tối ưu: lazy load trình xem PDF và video, preload bài kế tiếp; thời gian bắt đầu phát video ≤ 3 giây trên mạng 4G.
- [ ] Test: ít nhất 8 test component và 1 E2E (mở bài → xem PDF → chuyển bài).

### Ngày 16–22 (7 ngày) — Quy tắc hoàn thành bài (`lesson_completion_rules`) (FE + BE)

- [ ] BE: CRUD quy tắc quan hệ 1-1 với lesson (thời gian tối thiểu, % video tối thiểu mặc định 80, `require_quiz`, `require_assignment`).
- [ ] FE: màn hình cấu hình trong course builder cho từng bài.
- [ ] BE: `/lessons/{id}/completion-rule` kèm ít nhất 6 test.
- [ ] FE: màn hình cấu hình quy tắc theo Figma, hiển thị mô tả cách tính.
- [ ] Ngoại lệ: phần trăm ngoài khoảng 0–100 trả 400; quy tắc tham chiếu quiz / assignment không tồn tại trả 409; tạo trùng quy tắc cho 1 bài trả 409.
- [ ] Phi chức năng: thay đổi quy tắc không làm đổi trạng thái đã hoàn thành của học viên cũ (có ghi chú trong giao diện).

### Ngày 23–29 (7 ngày) — Tìm kiếm khóa học và cache catalog (BE)

- [ ] Làm tìm kiếm theo từ khóa (Postgres full-text hoặc trigram), lọc visibility / status, phân trang keyset.
- [ ] Thêm cache dữ liệu nóng (Caffeine hoặc Redis) chỉ khi đo được lợi ích; ghi kết quả đo vào ADR.
- [ ] BE: `/courses/search` chạy được, ít nhất 8 test.
- [ ] Ngoại lệ: cache bị xóa khi khóa đổi trạng thái hoặc visibility trong ≤ 5 giây; từ khóa rỗng hoặc ký tự đặc biệt không gây lỗi 500.
- [ ] Tối ưu: với 10.000 khóa học, p95 tìm kiếm ≤ 200 ms; so sánh EXPLAIN và thời gian trước / sau khi thêm index và cache.
- [ ] Tài liệu: ADR cache catalog (kết quả đo, TTL, chính sách xóa cache).
