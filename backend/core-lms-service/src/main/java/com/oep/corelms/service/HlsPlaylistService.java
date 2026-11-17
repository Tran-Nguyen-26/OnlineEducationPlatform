package com.oep.corelms.service;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.TranscodeStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Serves HLS playlists with per-segment presigned URLs. The browser asks the API (with its JWT) for the playlists, so access
 * control stays here, while the heavy part — the video segments — is downloaded straight from object storage / CDN.
 */
@Service
@RequiredArgsConstructor
public class HlsPlaylistService {

    private static final Pattern VARIANT = Pattern.compile("v\\d{1,2}");

    private final StorageService storage;

    /** Master playlist: variant references are rewritten to this API ({@code v0.m3u8}, resolved relative to the master URL). */
    public String master(LessonContent content) {
        requireReady(content);
        StringBuilder out = new StringBuilder();
        for (String line : storage.readText(content.getHlsPrefix() + "/master.m3u8").split("\\R")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                // "v0/index.m3u8" -> "v0.m3u8"
                out.append(line.substring(0, line.indexOf('/'))).append(".m3u8\n");
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    /** Variant playlist: every segment name becomes a short-lived presigned URL. */
    public String variant(LessonContent content, String variant) {
        requireReady(content);
        if (!VARIANT.matcher(variant).matches()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Unknown rendition");
        }
        String base = content.getHlsPrefix() + "/" + variant + "/";
        StringBuilder out = new StringBuilder();
        for (String line : storage.readText(base + "index.m3u8").split("\\R")) {
            if (!line.isBlank() && !line.startsWith("#")) {
                out.append(storage.presignView(base + line.trim())).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static void requireReady(LessonContent content) {
        if (content.getTranscodeStatus() != TranscodeStatus.READY || content.getHlsPrefix() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Adaptive stream is not ready");
        }
    }
}
