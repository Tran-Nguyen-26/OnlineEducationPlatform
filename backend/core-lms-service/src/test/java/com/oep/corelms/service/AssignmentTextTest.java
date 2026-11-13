package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

class AssignmentTextTest {
    @Test
    void acceptsVietnameseTextAndExtractsDocx() throws Exception {
        String text = "Bài làm được kiểm tra. ".repeat(12).strip();
        assertThat(AssignmentText.validate(text)).isEqualTo(text);
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("""
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                    <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body></w:document>
                    """.formatted(text)).getBytes(StandardCharsets.UTF_8));
        }
        assertThat(AssignmentText.read(new MockMultipartFile("file", "essay.docx", null, bytes.toByteArray())))
                .isEqualTo(text);
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.save(bytes);
        }
        assertThatThrownBy(() -> AssignmentText.read(new MockMultipartFile("file", "scan.pdf", null, bytes.toByteArray())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("50 từ");
    }
}
