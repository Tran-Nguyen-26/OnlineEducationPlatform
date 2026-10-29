package com.oep.corelms.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // Omit I, O to avoid confusion
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz"; // Omit l, o to avoid confusion
    private static final String DIGITS = "23456789";                  // Omit 0, 1 to avoid confusion
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL_CHARS = UPPER + LOWER + DIGITS + SPECIAL;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int DEFAULT_LENGTH = 12;

    private PasswordGenerator() {}

    public static String generateSecurePassword() {
        return generateSecurePassword(DEFAULT_LENGTH);
    }

    public static String generateSecurePassword(int length) {
        if (length < 8) {
            length = DEFAULT_LENGTH;
        }

        List<Character> characters = new ArrayList<>();

        characters.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        characters.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        characters.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        characters.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = 4; i < length; i++) {
            characters.add(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }

        Collections.shuffle(characters, RANDOM);

        StringBuilder sb = new StringBuilder(length);
        for (char ch : characters) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
