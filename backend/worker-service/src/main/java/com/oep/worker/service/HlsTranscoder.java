package com.oep.worker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class HlsTranscoder {
    @Value("${oep.media.ffmpeg:ffmpeg}")
    private String ffmpeg;
    @Value("${oep.media.ffprobe:ffprobe}")
    private String ffprobe;

    int transcode(Path input, Path output, Path log) throws IOException, InterruptedException {
        Files.createDirectories(output);
        run(List.of(ffprobe, "-v", "error", "-show_entries", "format=duration", "-of",
                "default=noprint_wrappers=1:nokey=1", input.toString()), log, Duration.ofMinutes(1));
        double seconds = Double.parseDouble(Files.readString(log).strip());
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > Integer.MAX_VALUE) {
            throw new IOException("Invalid source duration");
        }
        rendition(input, output, log, 854, 480, "1200k");
        rendition(input, output, log, 1280, 720, "2800k");
        Files.writeString(output.resolve("master.m3u8"), """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-INDEPENDENT-SEGMENTS
                #EXT-X-STREAM-INF:BANDWIDTH=1450000,RESOLUTION=854x480
                480p/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=3200000,RESOLUTION=1280x720
                720p/index.m3u8
                """);
        return (int) Math.ceil(seconds);
    }

    private void rendition(Path input, Path output, Path log, int width, int height, String bitrate)
            throws IOException, InterruptedException {
        Path target = Files.createDirectory(output.resolve(height + "p"));
        String scale = "scale=%d:%d:force_original_aspect_ratio=decrease:force_divisible_by=2,pad=%d:%d:(ow-iw)/2:(oh-ih)/2,setsar=1"
                .formatted(width, height, width, height);
        run(List.of(ffmpeg, "-nostdin", "-loglevel", "error", "-i", input.toString(),
                "-map", "0:v:0", "-map", "0:a:0?", "-vf", scale,
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-preset", "fast", "-b:v", bitrate,
                "-force_key_frames", "expr:gte(t,n_forced*6)", "-c:a", "aac", "-b:a", "128k",
                "-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments", "-hls_segment_filename", target.resolve("segment-%05d.ts").toString(),
                target.resolve("index.m3u8").toString()), log, Duration.ofMinutes(45));
    }

    private void run(List<String> command, Path log, Duration timeout) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IOException("Media processing command failed");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
