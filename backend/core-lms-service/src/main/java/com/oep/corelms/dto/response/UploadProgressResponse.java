package com.oep.corelms.dto.response;

import com.oep.coremodel.enums.VideoJobStatus;

import java.util.UUID;

public record UploadProgressResponse(
        UUID jobId,
        UUID contentId,
        VideoJobStatus stage,
        int progressPct,
        String currentRendition,
        String status,
        String hlsMasterKey,
        String message
) {}
