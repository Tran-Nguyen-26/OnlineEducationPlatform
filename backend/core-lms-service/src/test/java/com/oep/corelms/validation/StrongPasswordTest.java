package com.oep.corelms.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordTest {

    private final StrongPassword.Validator validator = new StrongPassword.Validator();

    @ParameterizedTest
    @ValueSource(strings = {"Student@123", "Adm1n-abcdefghijk", "tuyetvoi2026", "Abcdefg1"})
    void acceptsLongEnoughPasswordsWithLetterAndDigit(String password) {
        assertTrue(validator.isValid(password, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "onlyletters", "12345678", "password1", "Password1", "MATKHAU123"})
    void rejectsShortSingleClassAndCommonPasswords(String password) {
        assertFalse(validator.isValid(password, null));
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertTrue(validator.isValid(null, null));
    }
}
