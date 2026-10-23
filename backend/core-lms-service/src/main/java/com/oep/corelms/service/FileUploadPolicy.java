package com.oep.corelms.service;

import com.oep.coremodel.enums.ContentType;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FileUploadPolicy {
    static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private static final Map<ContentType, Set<String>> ALLOWED_TYPES = Map.of(
            ContentType.VIDEO, Set.of("video/mp4", "video/webm", "video/quicktime"),
            ContentType.PDF, Set.of("application/pdf"),
            ContentType.SLIDE, Set.of("application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            ContentType.DOCUMENT, Set.of("application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain", "text/csv"),
            ContentType.FILE, Set.of("application/pdf", "video/mp4", "video/webm", "video/quicktime",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain", "text/csv", "application/zip", "application/octet-stream"));

    private FileUploadPolicy() {}

    static boolean isAllowed(ContentType contentType, String mimeType) {
        return ALLOWED_TYPES.getOrDefault(contentType, Set.of())
                .contains(mimeType.toLowerCase(Locale.ROOT).trim());
    }

    static boolean isFileContent(ContentType type) {
        return ALLOWED_TYPES.containsKey(type);
    }
}
