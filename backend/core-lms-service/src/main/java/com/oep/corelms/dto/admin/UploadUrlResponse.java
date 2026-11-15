package com.oep.corelms.dto.admin;

public record UploadUrlResponse(String uploadUrl, String objectKey, long expiresInSeconds) {
}
