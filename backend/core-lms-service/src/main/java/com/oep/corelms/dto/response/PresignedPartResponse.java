package com.oep.corelms.dto.response;

public record PresignedPartResponse(
        int partNumber,
        String presignedUrl
) {}
