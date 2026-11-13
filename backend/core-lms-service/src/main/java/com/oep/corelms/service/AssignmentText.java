package com.oep.corelms.service;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

final class AssignmentText {
    static final int MAX_FILE = 20 * 1024 * 1024;
    private static final int MAX_TEXT = 100_000;
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}]*");

    private AssignmentText() {}

    static String read(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_FILE) throw invalid("Tệp phải có dung lượng tối đa 20 MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(file.getBytes())) {
                    var output = new java.io.StringWriter() {
                        @Override
                        public void write(String value) {
                            if (getBuffer().length() + value.length() > MAX_TEXT) throw invalid("Văn bản bài nộp quá dài");
                            super.write(value);
                        }
                    };
                    new PDFTextStripper().writeText(pdf, output);
                    return validate(output.toString());
                }
            }
            if (filename.endsWith(".docx")) {
                try (var zip = new ZipInputStream(file.getInputStream())) {
                    int remaining = 2 * MAX_FILE;
                    for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                        if ("word/document.xml".equals(entry.getName())) {
                            byte[] xml = zip.readNBytes(MAX_FILE + 1);
                            if (xml.length > MAX_FILE) throw invalid("Nội dung DOCX quá lớn");
                            return validate(docxText(xml));
                        }
                        // Bound decompression even when the document entry appears later.
                        int read = zip.readNBytes(Math.min(remaining, MAX_FILE) + 1).length;
                        remaining -= read;
                        if (remaining < 0 || read > MAX_FILE) throw invalid("Nội dung DOCX quá lớn");
                    }
                }
            }
        } catch (IOException | XMLStreamException ex) {
            throw invalid("Không đọc được văn bản trong tệp");
        }
        throw invalid("Chỉ hỗ trợ tệp PDF hoặc DOCX hợp lệ");
    }

    private static String docxText(byte[] xml) throws XMLStreamException {
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
        var text = new StringBuilder();
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "t".equals(reader.getLocalName())
                        && "http://schemas.openxmlformats.org/wordprocessingml/2006/main".equals(reader.getNamespaceURI())) {
                    text.append(reader.getElementText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "p".equals(reader.getLocalName())) {
                    text.append('\n');
                }
                if (text.length() > MAX_TEXT) throw invalid("Văn bản bài nộp quá dài");
            }
        } finally {
            reader.close();
        }
        return text.toString();
    }

    static String validate(String text) {
        String clean = text == null ? "" : text.strip().replace("\u0000", "");
        if (clean.length() > MAX_TEXT) throw invalid("Văn bản bài nộp quá dài");
        if (WORD.matcher(clean).results().limit(50).count() < 50
                && clean.codePoints().filter(Character::isLetter).limit(300).count() < 300)
            throw invalid("Bài nộp cần ít nhất 50 từ hoặc 300 ký tự chữ cái. PDF chỉ chứa ảnh không được chấp nhận.");
        return clean;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
