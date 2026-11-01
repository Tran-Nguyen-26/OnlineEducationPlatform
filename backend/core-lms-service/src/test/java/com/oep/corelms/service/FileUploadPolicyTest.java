package com.oep.corelms.service;

import com.oep.coremodel.enums.ContentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadPolicyTest {
    @Test
    void videoLimitIsTwoGiB() {
        assertEquals(2L * 1024 * 1024 * 1024, FileUploadPolicy.maxFileSizeBytes(ContentType.VIDEO));
    }

    @Test
    void pdfAndSlidesAreLimitedToFiftyMiB() {
        assertEquals(50L * 1024 * 1024, FileUploadPolicy.maxFileSizeBytes(ContentType.PDF));
        assertEquals(50L * 1024 * 1024, FileUploadPolicy.maxFileSizeBytes(ContentType.SLIDE));
    }

    @Test
    void onlyLargeVideosRequireMultipartUpload() {
        assertTrue(FileUploadPolicy.requiresMultipart(ContentType.VIDEO, 51L * 1024 * 1024));
        assertFalse(FileUploadPolicy.requiresMultipart(ContentType.VIDEO, 50L * 1024 * 1024));
        assertFalse(FileUploadPolicy.requiresMultipart(ContentType.PDF, 50L * 1024 * 1024));
    }

    @Test
    void rejectsUnsupportedMimeTypesByContentKind() {
        assertTrue(FileUploadPolicy.isAllowed(ContentType.VIDEO, "VIDEO/MP4"));
        assertFalse(FileUploadPolicy.isAllowed(ContentType.PDF, "video/mp4"));
    }
}
