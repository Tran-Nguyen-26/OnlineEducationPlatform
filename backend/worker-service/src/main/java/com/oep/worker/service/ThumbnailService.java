package com.oep.worker.service;

import com.oep.worker.config.FfmpegConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailService {

    private final FfmpegConfig ffmpegConfig;

    /**
     * Tạo thumbnail chính (1280x720) tại thời điểm 10% thời lượng của video.
     */
    public void generateThumbnail(Path videoPath, Path outputThumbPath, double duration) {
        try {
            if (outputThumbPath.getParent() != null) {
                Files.createDirectories(outputThumbPath.getParent());
            }

            double offset = Math.max(1.0, duration * 0.1);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegConfig.getBinary(),
                    "-ss", String.format("%.2f", offset),
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-vframes", "1",
                    "-vf", "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2",
                    "-y",
                    outputThumbPath.toAbsolutePath().toString()
            );

            Process process = pb.start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Timeout khi tạo thumbnail");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFmpeg tạo thumbnail trả về mã lỗi: " + process.exitValue());
            }

            log.info("Đã tạo thumbnail thành công: {}", outputThumbPath);

        } catch (Exception e) {
            log.error("Lỗi khi tạo thumbnail cho {}: {}", videoPath, e.getMessage());
            throw new RuntimeException("Tạo thumbnail thất bại", e);
        }
    }

    /**
     * Tạo Sprite Sheet 20 frame (5 cột x 4 hàng = 800x360 px, mỗi frame 160x90 px)
     * phục vụ tính năng preview video scrubbing trên timeline player.
     */
    public void generateSpriteSheet(Path videoPath, Path outputSpritePath, double duration) {
        try {
            if (outputSpritePath.getParent() != null) {
                Files.createDirectories(outputSpritePath.getParent());
            }

            double interval = Math.max(1.0, duration / 20.0);
            String filter = String.format("fps=1/%.2f,scale=160:90,tile=5x4", interval);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegConfig.getBinary(),
                    "-i", videoPath.toAbsolutePath().toString(),
                    "-vf", filter,
                    "-frames:v", "1",
                    "-y",
                    outputSpritePath.toAbsolutePath().toString()
            );

            Process process = pb.start();
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Timeout khi tạo sprite sheet");
            }

            if (process.exitValue() != 0) {
                throw new RuntimeException("FFmpeg tạo sprite sheet trả về mã lỗi: " + process.exitValue());
            }

            log.info("Đã tạo sprite sheet thành công: {}", outputSpritePath);

        } catch (Exception e) {
            log.error("Lỗi khi tạo sprite sheet cho {}: {}", videoPath, e.getMessage());
            throw new RuntimeException("Tạo sprite sheet thất bại", e);
        }
    }
}
