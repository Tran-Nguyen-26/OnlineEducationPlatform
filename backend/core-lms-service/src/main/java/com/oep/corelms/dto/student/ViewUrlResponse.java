package com.oep.corelms.dto.student;

/** hlsUrl: API path of the adaptive-stream master playlist when the video has been transcoded, otherwise null. */
public record ViewUrlResponse(String viewUrl, long expiresInSeconds, String hlsUrl) {
}
