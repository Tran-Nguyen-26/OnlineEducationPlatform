package com.oep.coremodel.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    COURSE_NOT_OPEN(HttpStatus.CONFLICT),
    COURSE_FULL(HttpStatus.CONFLICT),
    INVALID_INVITE_CODE(HttpStatus.FORBIDDEN),
    ALREADY_ENROLLED(HttpStatus.CONFLICT),
    QUIZ_LOCKED(HttpStatus.CONFLICT),
    ATTEMPT_LIMIT_REACHED(HttpStatus.CONFLICT),
    DEADLINE_PASSED(HttpStatus.CONFLICT),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) { this.status = status; }

    public HttpStatus status() { return status; }
}
