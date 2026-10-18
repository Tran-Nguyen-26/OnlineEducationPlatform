package com.oep.corelms.dto.response;

import java.util.UUID;

public record InitMultipartResponse(
        UUID contentId,
        String uploadId,
        String objectKey
) {}
