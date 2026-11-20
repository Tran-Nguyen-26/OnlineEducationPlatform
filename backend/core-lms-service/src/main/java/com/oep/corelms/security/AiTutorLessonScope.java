package com.oep.corelms.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Canonical digest binding a private chat request's readable lessons to its service assertion. */
public final class AiTutorLessonScope {

    private AiTutorLessonScope() {
    }

    public static String sha256(List<UUID> lessonIds) {
        String canonical = lessonIds.stream()
                .map(UUID::toString)
                .distinct()
                .sorted()
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
