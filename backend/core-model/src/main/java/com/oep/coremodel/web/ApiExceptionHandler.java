package com.oep.coremodel.web;

import com.oep.coremodel.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * RFC 7807 error bodies ({@code application/problem+json}) shared by every servlet service. Besides the standard
 * {@code type/title/status/detail} members each response keeps the legacy {@code code} and {@code message} fields the
 * frontend already reads, so the format is richer without breaking any client.
 * Services extend this class and add {@code @RestControllerAdvice}.
 */
public abstract class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        return problem(ex.getCode().status(), ex.getCode().name(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yêu cầu không hợp lệ");
    }

    /** Safety net for deletes blocked by a FK still referencing the row. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", "Bản ghi này vẫn đang được tham chiếu nên không thể thay đổi/xóa");
    }

    /** Two writers raced on the same @Version row: the loser gets a clear 409 instead of silently overwriting. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "STALE_VERSION", "Dữ liệu vừa được người khác cập nhật. Hãy tải lại rồi thử lại.");
    }

    protected static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String code, String message) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, message);
        pd.setType(URI.create("https://oep.local/problems/" + code.toLowerCase().replace('_', '-')));
        pd.setTitle(code);
        pd.setProperty("code", code);
        pd.setProperty("message", message);
        return ResponseEntity.status(status).body(pd);
    }
}
