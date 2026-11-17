package com.oep.corelms.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Set;

/** Password policy: 8+ chars with a letter and a digit, and not one of the most common passwords. */
@Documented
@Constraint(validatedBy = StrongPassword.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Mật khẩu cần ít nhất 8 ký tự, gồm cả chữ và số, và không quá phổ biến";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {
        private static final Set<String> COMMON = Set.of("password", "password1", "12345678", "123456789", "qwerty123",
                "11111111", "iloveyou1", "abc12345", "admin123", "matkhau123", "letmein123");

        @Override
        public boolean isValid(String v, ConstraintValidatorContext ctx) {
            if (v == null) return true; // @NotBlank reports absence
            boolean letter = v.chars().anyMatch(Character::isLetter);
            boolean digit = v.chars().anyMatch(Character::isDigit);
            return v.length() >= 8 && letter && digit && !COMMON.contains(v.toLowerCase());
        }
    }
}
