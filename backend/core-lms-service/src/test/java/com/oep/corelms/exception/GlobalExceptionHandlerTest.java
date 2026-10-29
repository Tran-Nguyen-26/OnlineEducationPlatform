package com.oep.corelms.exception;

import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBusinessException_shouldReturnCorrectHttpStatusAndError() {
        BusinessException ex = new BusinessException(ErrorCode.BAD_CREDENTIALS, "Sai email hoặc mật khẩu");

        ResponseEntity<Map<String, Object>> res = handler.handleBusinessException(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody())
                .containsEntry("error", ErrorCode.BAD_CREDENTIALS.name())
                .containsEntry("message", "Sai email hoặc mật khẩu")
                .containsEntry("status", 401);
    }

    @Test
    void handleValidationExceptions_shouldReturnBadRequestWithDetails() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("user", "email", "Email không được để trống");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<Map<String, Object>> res = handler.handleValidationExceptions(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody())
                .containsEntry("error", ErrorCode.VALIDATION_FAILED.name())
                .containsEntry("status", 400);

        @SuppressWarnings("unchecked")
        Map<String, String> details = (Map<String, String>) res.getBody().get("details");
        assertThat(details).containsEntry("email", "Email không được để trống");
    }

    @Test
    void handleGeneralException_shouldReturnInternalServerError() {
        RuntimeException ex = new RuntimeException("Unexpected database failure");

        ResponseEntity<Map<String, Object>> res = handler.handleGeneralException(ex);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody())
                .containsEntry("error", "INTERNAL_SERVER_ERROR")
                .containsEntry("message", "Unexpected database failure")
                .containsEntry("status", 500);
    }
}
