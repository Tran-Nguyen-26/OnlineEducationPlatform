package com.oep.worker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.worker.config.FfmpegConfig;
import com.oep.worker.model.VideoMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProbeService {

    private final FfmpegConfig ffmpegConfig;
    private final ObjectMapper objectMapper;

    /**
     * Dùng ffprobe để phân tích thông số kỹ thuật của video (độ phân giải, thời lượng, codec, fps).
     */
    public VideoMetadata probe(Path videoPath) {
        log.info("Bắt đầu ffprobe cho file: {}", videoPath);

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegConfig.getFfprobe(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                videoPath.toAbsolutePath().toString()
        );

        StringBuilder output = new StringBuilder();

        try {
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("FFprobe timeout sau 60 giây");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFprobe trả về mã lỗi: " + process.exitValue());
            }

            JsonNode root = objectMapper.readTree(output.toString());
            JsonNode streams = root.get("streams");
            JsonNode format = root.get("format");

            int width = 0;
            int height = 0;
            double duration = 0.0;
            double fps = 30.0;
            String videoCodec = "unknown";
            String audioCodec = "unknown";

            if (streams != null && streams.isArray()) {
                for (JsonNode s : streams) {
                    String codecType = s.path("codec_type").asText();
                    if ("video".equalsIgnoreCase(codecType) && width == 0) {
                        width = s.path("width").asInt(1280);
                        height = s.path("height").asInt(720);
                        videoCodec = s.path("codec_name").asText("h264");

                        String frameRateStr = s.path("r_frame_rate").asText("30/1");
                        fps = parseFrameRate(frameRateStr);
                    } else if ("audio".equalsIgnoreCase(codecType) && "unknown".equals(audioCodec)) {
                        audioCodec = s.path("codec_name").asText("aac");
                    }
                }
            }

            if (format != null && format.has("duration")) {
                duration = format.path("duration").asDouble(0.0);
            }

            log.info("Kết quả ffprobe: {}x{}, duration={}s, fps={}, videoCodec={}, audioCodec={}",
                    width, height, duration, fps, videoCodec, audioCodec);

            return new VideoMetadata(width, height, duration, fps, videoCodec, audioCodec);

        } catch (Exception e) {
            log.error("Lỗi khi chạy ffprobe trên file {}: {}", videoPath, e.getMessage());
            throw new RuntimeException("Không thể phân tích metadata video: " + e.getMessage(), e);
        }
    }

    private double parseFrameRate(String frameRateStr) {
        try {
            if (frameRateStr.contains("/")) {
                String[] parts = frameRateStr.split("/");
                double num = Double.parseDouble(parts[0]);
                double den = Double.parseDouble(parts[1]);
                return den != 0 ? num / den : 30.0;
            }
            return Double.parseDouble(frameRateStr);
        } catch (Exception e) {
            return 30.0;
        }
    }
}
