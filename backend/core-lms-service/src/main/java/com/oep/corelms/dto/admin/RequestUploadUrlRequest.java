package com.oep.corelms.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** fileSize (bytes) lets the API refuse an oversized file before the browser starts uploading it. */
public record RequestUploadUrlRequest(@NotBlank String fileName, @NotBlank String mimeType, Long fileSize) {
    /** Callers that do not know the size up front (the check then happens when the upload is completed). */
    public RequestUploadUrlRequest(String fileName, String mimeType) {
        this(fileName, mimeType, null);
    }
}
