package com.oep.corelms.dto.response;

import com.oep.coremodel.enums.UploadStatus;

import java.util.UUID;

public record ConfirmUploadResponse(
        UUID contentId,
        UploadStatus uploadStatus,
        String message
) {}
