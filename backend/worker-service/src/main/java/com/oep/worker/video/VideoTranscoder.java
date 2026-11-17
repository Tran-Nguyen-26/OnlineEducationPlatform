package com.oep.worker.video;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs ffmpeg to turn one source video into an HLS ladder: a 720p and a 360p rendition (never upscaled beyond the
 * source), 6-second segments, one master playlist. Output layout under {@code outDir}:
 * {@code master.m3u8}, {@code v0/index.m3u8 + seg_0000.ts ...}, {@code v1/...}.
 */
@Slf4j
@Component
public class VideoTranscoder {

    private static final long TIMEOUT_MINUTES = 30;

    public void toHls(Path source, Path outDir) throws IOException, InterruptedException {
        boolean hasAudio = probe(source, "a");
        int height = sourceHeight(source);
        int top = Math.min(720, height > 0 ? height : 720);
        int low = Math.min(360, top);
        boolean single = low == top; // tiny source: one rendition is enough

        List<String> cmd = new ArrayList<>(List.of("ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-threads", "2", "-i", source.toString()));
        StringBuilder filter = new StringBuilder();
        List<String> maps = new ArrayList<>();
        StringBuilder varMap = new StringBuilder();
        if (single) {
            filter.append("[0:v]scale=-2:").append(top).append("[v0o]");
        } else {
            filter.append("[0:v]split=2[a][b];[a]scale=-2:").append(top).append("[v0o];[b]scale=-2:").append(low).append("[v1o]");
        }
        cmd.addAll(List.of("-filter_complex", filter.toString()));
        int renditions = single ? 1 : 2;
        for (int i = 0; i < renditions; i++) {
            maps.addAll(List.of("-map", "[v" + i + "o]"));
            if (hasAudio) maps.addAll(List.of("-map", "a:0"));
            varMap.append(i > 0 ? " " : "").append("v:").append(i).append(hasAudio ? ",a:" + i : "");
        }
        cmd.addAll(maps);
        cmd.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0"));
        for (int i = 0; i < renditions; i++) {
            cmd.addAll(List.of("-b:v:" + i, i == 0 && !single && top >= 720 ? "2500k" : (single ? "1500k" : "800k")));
        }
        if (hasAudio) cmd.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2"));
        cmd.addAll(List.of("-f", "hls", "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", outDir.resolve("v%v/seg_%04d.ts").toString(),
                "-master_pl_name", "master.m3u8", "-var_stream_map", varMap.toString(),
                outDir.resolve("v%v/index.m3u8").toString()));
        for (int i = 0; i < renditions; i++) Files.createDirectories(outDir.resolve("v" + i));
        run(cmd);
    }

    private boolean probe(Path source, String streamType) throws IOException, InterruptedException {
        String out = capture(List.of("ffprobe", "-v", "error", "-select_streams", streamType, "-show_entries", "stream=index", "-of", "csv=p=0", source.toString()));
        return !out.isBlank();
    }

    private int sourceHeight(Path source) throws IOException, InterruptedException {
        String out = capture(List.of("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=height", "-of", "csv=p=0", source.toString()));
        try { return Integer.parseInt(out.trim()); } catch (NumberFormatException e) { return 0; }
    }

    private String capture(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IOException("ffprobe failed: " + out);
        return out;
    }

    private void run(List<String> cmd) throws IOException, InterruptedException {
        log.info("ffmpeg: {}", String.join(" ", cmd));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new IOException("ffmpeg timed out after " + TIMEOUT_MINUTES + " minutes");
        }
        if (p.exitValue() != 0) throw new IOException("ffmpeg exited with " + p.exitValue() + ": " + out);
    }
}
