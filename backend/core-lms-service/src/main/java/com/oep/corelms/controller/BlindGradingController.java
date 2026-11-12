package com.oep.corelms.controller;

import com.oep.corelms.service.BlindGradingService;
import com.oep.corelms.service.BlindGradingService.Claim;
import com.oep.corelms.service.BlindGradingService.QueueItem;
import com.oep.corelms.service.BlindGradingService.Result;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses/{course}/grading")
@RequiredArgsConstructor
public class BlindGradingController {
    private final BlindGradingService grading;

    public record TokenRequest(@NotNull UUID token) {}
    public record GradeRequest(@NotNull UUID token, @NotEmpty @Size(max = 100) Map<String, BigDecimal> scores,
                               @Size(max = 10000) String feedback) {}
    public record AppealRequest(@NotBlank @Size(max = 255) String criterion,
                                @NotBlank @Size(max = 10000) String reason) {}

    @GetMapping
    public List<QueueItem> queue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course,
                                 @RequestParam(defaultValue = "0") int page) {
        return grading.queue(actor(jwt), course, page);
    }

    @PostMapping("/{id}/claim")
    public Claim claim(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID id) {
        return grading.claim(actor(jwt), course, id);
    }

    @PostMapping("/{id}/renew")
    public Claim renew(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID id,
                       @Valid @RequestBody TokenRequest request) {
        return grading.renew(actor(jwt), course, id, request.token());
    }

    @PostMapping("/{id}/grade")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grade(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID id,
                      @Valid @RequestBody GradeRequest request) {
        grading.grade(actor(jwt), course, id, request.token(), request.scores(), request.feedback());
    }

    @GetMapping("/{id}/result")
    public Result result(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID id) {
        return grading.result(actor(jwt), course, id);
    }

    @PostMapping("/{id}/appeal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void appeal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @PathVariable UUID id,
                       @Valid @RequestBody AppealRequest request) {
        grading.appeal(actor(jwt), course, id, request.criterion(), request.reason());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("code", "GRADING_STATE_CONFLICT", "message", "Submission changed concurrently"));
    }

    private UUID actor(Jwt jwt) {
        if (jwt == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Authentication required");
        }
        return UUID.fromString(jwt.getSubject());
    }
}
