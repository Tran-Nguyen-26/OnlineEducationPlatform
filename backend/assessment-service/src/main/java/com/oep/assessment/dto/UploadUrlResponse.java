package com.oep.assessment.dto;

public record UploadUrlResponse(String uploadUrl, String objectKey, long expiresInSeconds) {
}
