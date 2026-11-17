package com.oep.assessment.web;

import com.oep.coremodel.web.ApiExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** All handlers (RFC 7807 problem+json) live in {@link ApiExceptionHandler}. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandler {
}
