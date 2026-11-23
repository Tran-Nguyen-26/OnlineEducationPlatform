package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.BankQuestionResponse;
import com.oep.corelms.dto.admin.QuestionImportResult;
import com.oep.corelms.dto.admin.QuestionTransferItem;
import com.oep.corelms.service.AdminQuizQuestionService;
import com.oep.corelms.service.QuestionBankTransferService;
import com.oep.corelms.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminQuestionBankController {

    private final AdminQuizQuestionService service;
    private final AuditLogService auditLogService;
    private final QuestionBankTransferService transfer;

    @GetMapping("/courses/{courseId}/question-bank")
    public List<BankQuestionResponse> bank(@PathVariable UUID courseId) {
        return service.bank(courseId);
    }

    /** format=json (default) or csv. */
    @GetMapping("/courses/{courseId}/question-bank/export")
    public ResponseEntity<?> export(@PathVariable UUID courseId, @RequestParam(defaultValue = "json") String format) {
        List<QuestionTransferItem> items = transfer.export(courseId);
        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"question-bank.csv\"")
                    .contentType(new org.springframework.http.MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                    .body(transfer.toCsv(items));
        }
        return ResponseEntity.ok(items);
    }

    @PostMapping("/courses/{courseId}/question-bank/import")
    public QuestionImportResult importJson(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                           @RequestBody List<QuestionTransferItem> items) {
        QuestionImportResult r = transfer.importItems(courseId, UUID.fromString(jwt.getSubject()), items);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "IMPORT_BANK_QUESTIONS", "COURSE", courseId, Map.of("imported", r.imported()));
        return r;
    }

    @PostMapping(value = "/courses/{courseId}/question-bank/import-csv", consumes = {"text/csv", "text/plain"})
    public QuestionImportResult importCsv(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @RequestBody String csv) {
        QuestionImportResult r = transfer.importItems(courseId, UUID.fromString(jwt.getSubject()), transfer.fromCsv(csv));
        auditLogService.record(UUID.fromString(jwt.getSubject()), "IMPORT_BANK_QUESTIONS", "COURSE", courseId, Map.of("imported", r.imported()));
        return r;
    }

    @DeleteMapping("/question-bank/{questionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID questionId) {
        service.deleteFromBank(questionId);
        auditLogService.record(UUID.fromString(jwt.getSubject()), "DELETE_BANK_QUESTION", "QUESTION", questionId, Map.of());
        return ResponseEntity.noContent().build();
    }
}
