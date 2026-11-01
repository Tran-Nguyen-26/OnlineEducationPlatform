package com.oep.corelms.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextContentSanitizerTest {
    @Test
    void preservesBasicFormattingAndRemovesScriptElements() {
        String sanitized = TextContentSanitizer.sanitize("<p>Hello <strong>class</strong></p><script>alert(1)</script>");

        assertTrue(sanitized.contains("<p>Hello <strong>class</strong></p>"));
        assertFalse(sanitized.toLowerCase().contains("<script"));
        assertFalse(sanitized.contains("alert(1)"));
    }

    @Test
    void removesDangerousLinkProtocolsAndEventAttributes() {
        String sanitized = TextContentSanitizer.sanitize("<a href=\"javascript:alert(1)\" onclick=\"alert(2)\">open</a>");

        assertFalse(sanitized.contains("javascript:"));
        assertFalse(sanitized.contains("onclick"));
        assertFalse(sanitized.contains("alert("));
        assertTrue(sanitized.contains("open"));
    }
}
