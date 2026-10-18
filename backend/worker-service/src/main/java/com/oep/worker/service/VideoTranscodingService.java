package com.oep.worker.service;

import com.oep.worker.config.FfmpegConfig;
import com.oep.worker.model.VideoMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoTranscodingService {

    public record RenditionConfig(
            String name,
            int width,
            int height,
            int videoBitrateK,
            int audioBitrateK,
            String profile,
            int bandwidth
    ) {}

    private static final List<RenditionConfig> ALL_RENDITIONS = List.of(
            new RenditionConfig("360p", 640, 360, 500, 96, "baseline", 596000),
            new RenditionConfig("480p", 854, 480, 1000, 128, "main", 1128000),
            new RenditionConfig("720p", 1280, 720, 2500, 192, "high", 2692000),
            new RenditionConfig("1080p", 1920, 1080, 5000, 192, "high", 5192000)
    );

    private static final Pattern TIME_PATTERN = Pattern.compile("time=([0-9]{2}):([0-9]{2}):([0-9]{2}\\.[0-9]+)");

    private final FfmpegConfig ffmpegConfig;

    /**
     * Xác định danh sách rendition cần tạo (quy tắc không upscale, tối thiểu 1 rendition).
     */
    public List<RenditionConfig> getApplicableRenditions(VideoMetadata metadata) {
        int sourceHeight = metadata.height() > 0 ? metadata.height() : 720;
        List<RenditionConfig> applicable = ALL_RENDITIONS.stream()
                .filter(r -> r.height() <= sourceHeight)
                .toList();

        if (applicable.isEmpty()) {
            return List.of(ALL_RENDITIONS.getFirst());
        }
        return applicable;
    }

    /**
     * Transcode video thành các rendition HLS và tạo master.m3u8.
     *
     * @param videoPath        Đường dẫn file video đầu vào
     * @param outputDir        Thư mục chứa kết quả (/tmp/oep/{jobId})
     * @param metadata         Thông tin metadata từ ffprobe
     * @param progressCallback Callback báo tiến độ (progressPct: 10->85, currentRendition)
     * @return Danh sách tên rendition đã hoàn tất (ví dụ: ["360p", "480p", "720p"])
     */
    public List<String> transcodeHls(
            Path videoPath,
            Path outputDir,
            VideoMetadata metadata,
            BiConsumer<Integer, String> progressCallback
    ) {
        List<RenditionConfig> renditions = getApplicableRenditions(metadata);
        List<String> renditionsDone = new ArrayList<>();
        double totalDuration = Math.max(1.0, metadata.durationSeconds());

        int totalRenditions = renditions.size();

        for (int i = 0; i < totalRenditions; i++) {
            RenditionConfig rendition = renditions.get(i);
            Path renditionDir = outputDir.resolve(rendition.name());

            try {
                Files.createDirectories(renditionDir);
            } catch (Exception e) {
                throw new RuntimeException("Không thể tạo thư mục rendition: " + renditionDir, e);
            }

            Path playlistFile = renditionDir.resolve("playlist.m3u8");
            Path segmentPattern = renditionDir.resolve("%03d.ts");

            log.info("Bắt đầu transcode rendition {} ({}/{}): resolution={}x{}, videoBitrate={}k",
                    rendition.name(), i + 1, totalRenditions, rendition.width(), rendition.height(), rendition.videoBitrateK());

            double basePct = 10.0 + ((double) i / totalRenditions) * 75.0;
            double stepPct = 75.0 / totalRenditions;

            List<String> command = new ArrayList<>(List.of(
                    ffmpegConfig.getBinary(),
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-vf", String.format("scale=%d:%d:force_original_aspect_ratio=decrease,pad=%d:%d:(ow-iw)/2:(oh-ih)/2",
                            rendition.width(), rendition.height(), rendition.width(), rendition.height()),
                    "-c:v", "libx264",
                    "-profile:v", rendition.profile(),
                    "-b:v", rendition.videoBitrateK() + "k",
                    "-maxrate", (int)(rendition.videoBitrateK() * 1.1) + "k",
                    "-bufsize", (rendition.videoBitrateK() * 2) + "k",
                    "-c:a", "aac",
                    "-b:a", rendition.audioBitrateK() + "k",
                    "-ar", "44100",
                    "-ac", "2",
                    "-hls_time", "10",
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", segmentPattern.toAbsolutePath().toString(),
                    "-y",
                    playlistFile.toAbsolutePath().toString()
            ));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            try {
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = TIME_PATTERN.matcher(line);
                        if (matcher.find()) {
                            double currentSeconds = parseTimeToSeconds(matcher.group(1), matcher.group(2), matcher.group(3));
                            double renditionPct = Math.min(1.0, currentSeconds / totalDuration);
                            int overallPct = (int) Math.min(85.0, basePct + renditionPct * stepPct);
                            progressCallback.accept(overallPct, rendition.name());
                        }
                    }
                }

                boolean finished = process.waitFor(120, TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    throw new RuntimeException("FFmpeg timeout cho rendition " + rendition.name());
                }

                if (process.exitValue() != 0) {
                    throw new RuntimeException("FFmpeg lỗi khi transcode " + rendition.name() + ", exitCode=" + process.exitValue());
                }

                renditionsDone.add(rendition.name());
                log.info("Hoàn thành transcode rendition: {}", rendition.name());

            } catch (Exception e) {
                log.error("Lỗi khi transcode rendition {}: {}", rendition.name(), e.getMessage());
                throw new RuntimeException("Lỗi transcode: " + e.getMessage(), e);
            }
        }

        // Tạo file master.m3u8
        createMasterPlaylist(outputDir, renditions);

        return renditionsDone;
    }

    private void createMasterPlaylist(Path outputDir, List<RenditionConfig> renditions) {
        StringBuilder masterContent = new StringBuilder();
        masterContent.append("#EXTM3U\n");
        masterContent.append("#EXT-X-VERSION:3\n\n");

        for (RenditionConfig r : renditions) {
            masterContent.append(String.format(
                    "#EXT-X-STREAM-INF:BANDWIDTH=%d,RESOLUTION=%dx%d,NAME=\"%s\"\n",
                    r.bandwidth(), r.width(), r.height(), r.name()
            ));
            masterContent.append(String.format("%s/playlist.m3u8\n\n", r.name()));
        }

        Path masterPath = outputDir.resolve("master.m3u8");
        try {
            Files.writeString(masterPath, masterContent.toString());
            log.info("Đã tạo master playlist thành công tại: {}", masterPath);
        } catch (Exception e) {
            throw new RuntimeException("Không thể ghi file master.m3u8", e);
        }
    }

    private double parseTimeToSeconds(String hours, String minutes, String seconds) {
        try {
            return Double.parseDouble(hours) * 3600
                    + Double.parseDouble(minutes) * 60
                    + Double.parseDouble(seconds);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
