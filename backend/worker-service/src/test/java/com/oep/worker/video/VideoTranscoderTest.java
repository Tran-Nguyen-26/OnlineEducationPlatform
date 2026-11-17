package com.oep.worker.video;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real ffmpeg (present on GitHub runners and in the worker image); skipped where it is not installed. */
class VideoTranscoderTest {

    private static boolean ffmpegAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void producesMasterPlaylistAndTwoRenditionsWithSegments(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(ffmpegAvailable(), "ffmpeg not installed");
        Path source = dir.resolve("in.mp4");
        Process gen = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi", "-i", "testsrc=duration=8:size=1280x720:rate=30", "-f", "lavfi", "-i", "sine=frequency=440:duration=8",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", source.toString()).redirectErrorStream(true).start();
        gen.getInputStream().readAllBytes();
        assertTrue(gen.waitFor(60, TimeUnit.SECONDS) && gen.exitValue() == 0, "test video generation failed");

        Path out = dir.resolve("hls");
        new VideoTranscoder().toHls(source, out);

        String master = Files.readString(out.resolve("master.m3u8"));
        assertTrue(master.contains("v0/index.m3u8") && master.contains("v1/index.m3u8"), master);
        assertTrue(master.contains("RESOLUTION=1280x720") && master.contains("RESOLUTION=640x360"), master);
        for (String v : new String[]{"v0", "v1"}) {
            String playlist = Files.readString(out.resolve(v).resolve("index.m3u8"));
            assertTrue(playlist.contains("#EXT-X-ENDLIST"));
            try (Stream<Path> files = Files.list(out.resolve(v))) {
                assertTrue(files.anyMatch(f -> f.getFileName().toString().endsWith(".ts")), "no segments in " + v);
            }
        }
    }
}
