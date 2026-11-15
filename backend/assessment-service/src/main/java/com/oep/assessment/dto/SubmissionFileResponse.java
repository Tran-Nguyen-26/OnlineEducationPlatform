package com.oep.assessment.dto;

import com.oep.coremodel.entity.assignment.SubmissionFile;
import com.oep.coremodel.enums.UploadStatus;

import java.util.UUID;

public record SubmissionFileResponse(UUID id, String fileName, String fileType, long fileSize, UploadStatus uploadStatus) {
    public static SubmissionFileResponse from(SubmissionFile file) {
        return new SubmissionFileResponse(file.getId(), file.getFileName(), file.getFileType(), file.getFileSize(), file.getUploadStatus());
    }
}
