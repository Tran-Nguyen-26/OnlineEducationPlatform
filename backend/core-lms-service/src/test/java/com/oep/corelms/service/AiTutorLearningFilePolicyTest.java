package com.oep.corelms.service;

import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiTutorLearningFilePolicyTest {

    @Test
    void acceptsOnlyMatchingAiFileExtensionsAndMimeTypes() {
        assertEquals(20L * 1024 * 1024,
                AiTutorLearningFilePolicy.maximumBytes(ContentType.PDF, "chapter.PDF", "application/pdf"));
        assertEquals(5L * 1024 * 1024,
                AiTutorLearningFilePolicy.maximumBytes(ContentType.FILE, "notes.md", "text/markdown; charset=utf-8"));
        assertEquals(5L * 1024 * 1024,
                AiTutorLearningFilePolicy.maximumBytes(ContentType.FILE, "data.JSON", "application/json"));
        assertEquals(0, AiTutorLearningFilePolicy.maximumBytes(ContentType.FILE, "worksheet.txt", "text/plain"));
    }

    @Test
    void rejectsDisguisedOrMismatchedLearningFiles() {
        for (Object[] bad : new Object[][] {
                {ContentType.PDF, "chapter.txt", "application/pdf"},
                {ContentType.PDF, "chapter.pdf", "text/plain"},
                {ContentType.FILE, "notes.md", "application/json"},
                {ContentType.FILE, "data.json", "text/plain"},
                {ContentType.FILE, "data.txt", "application/json"},
                {ContentType.FILE, "notes.txt", "text/markdown"},
        }) {
            BusinessException error = assertThrows(BusinessException.class, () ->
                    AiTutorLearningFilePolicy.maximumBytes(
                            (ContentType) bad[0], (String) bad[1], (String) bad[2]));
            assertEquals(ErrorCode.VALIDATION_FAILED, error.getCode());
        }
    }
}
