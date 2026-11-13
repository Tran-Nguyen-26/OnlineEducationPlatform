package com.oep.corelms.controller;

import com.oep.corelms.dto.enrollment.AccessCodeDtos;
import com.oep.corelms.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/access-codes")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccessCodeController {
    private final EnrollmentService enrollments;

    @PostMapping("/batches")
    public ResponseEntity<AccessCodeDtos.Batch> issue(@Valid @RequestBody AccessCodeDtos.CreateBatch request) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(enrollments.issueBatch(request));
    }

    @GetMapping("/batches/{id}")
    public List<AccessCodeDtos.CodeStatus> batch(@PathVariable UUID id) {
        return enrollments.listBatch(id);
    }

    @PostMapping("/{id}/revoke")
    public AccessCodeDtos.CodeStatus revoke(@PathVariable UUID id) {
        return enrollments.revokeCode(id);
    }
}
