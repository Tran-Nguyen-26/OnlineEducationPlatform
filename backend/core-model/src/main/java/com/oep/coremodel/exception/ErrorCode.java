package com.oep.coremodel.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // --- General ---
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    // --- Course / Enrollment ---
    COURSE_NOT_OPEN(HttpStatus.CONFLICT),
    COURSE_FULL(HttpStatus.CONFLICT),
    INVALID_INVITE_CODE(HttpStatus.FORBIDDEN),
    ALREADY_ENROLLED(HttpStatus.CONFLICT),
    // --- Assessment ---
    QUIZ_LOCKED(HttpStatus.CONFLICT),
    ATTEMPT_LIMIT_REACHED(HttpStatus.CONFLICT),
    DEADLINE_PASSED(HttpStatus.CONFLICT),
    // --- Identity & Auth ---
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    MUST_CHANGE_PASSWORD(HttpStatus.FORBIDDEN),
    // --- Forgot Password / OTP ---
    OTP_EXPIRED(HttpStatus.BAD_REQUEST),
    INVALID_OTP(HttpStatus.BAD_REQUEST),
    OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.BAD_REQUEST),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    GOOGLE_ACCOUNT_NO_PASSWORD(HttpStatus.BAD_REQUEST),
    APPLICATION_ALREADY_PENDING(HttpStatus.CONFLICT),
    APPLICATION_ALREADY_REVIEWED(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) { this.status = status; }

    public HttpStatus status() { return status; }
}
