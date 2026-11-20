package com.oep.corelms.service;

import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;

import java.util.Locale;
import java.util.Set;

/** M2 upload policy for the file formats eligible for M7 indexing. */
final class AiTutorLearningFilePolicy {

    static final long PDF_MAX_BYTES = 20L * 1024 * 1024;
    static final long MARKDOWN_JSON_MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> MARKDOWN_MIME = Set.of("text/markdown", "text/x-markdown", "text/plain");

    private AiTutorLearningFilePolicy() {
    }

    /** Returns the actual-size limit for an M7-eligible upload, or zero for unrelated M2 files. */
    static long maximumBytes(ContentType type, String fileName, String mimeType) {
        String extension = extension(fileName);
        String mime = baseMime(mimeType);
        if (type == ContentType.PDF) {
            if (!".pdf".equals(extension) || !"application/pdf".equals(mime)) {
                throw invalid("PDF content requires a .pdf file with application/pdf MIME type");
            }
            return PDF_MAX_BYTES;
        }
        if (type != ContentType.FILE) {
            return 0;
        }
        if (".md".equals(extension)) {
            if (!MARKDOWN_MIME.contains(mime)) {
                throw invalid("Markdown content requires a Markdown or plain-text MIME type");
            }
            return MARKDOWN_JSON_MAX_BYTES;
        }
        if (".json".equals(extension)) {
            if (!"application/json".equals(mime)) {
                throw invalid("JSON content requires application/json MIME type");
            }
            return MARKDOWN_JSON_MAX_BYTES;
        }
        if ("application/json".equals(mime) || MARKDOWN_MIME.contains(mime) && !"text/plain".equals(mime)) {
            throw invalid("Learning-content MIME type does not match the file extension");
        }
        return 0;
    }

    static String baseMime(String value) {
        return value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
