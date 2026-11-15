package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.AdminEnrollmentResponse;
import com.oep.corelms.dto.admin.UpdateEnrollmentStatusRequest;
import com.oep.corelms.service.AdminEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminEnrollmentController {

    private final AdminEnrollmentService enrollmentService;

    @GetMapping("/courses/{courseId}/enrollments")
    public List<AdminEnrollmentResponse> list(@PathVariable UUID courseId) {
        return enrollmentService.list(courseId);
    }

    @PatchMapping("/enrollments/{enrollmentId}/status")
    public AdminEnrollmentResponse updateStatus(@PathVariable UUID enrollmentId,
                                                 @Valid @RequestBody UpdateEnrollmentStatusRequest request) {
        return enrollmentService.updateStatus(enrollmentId, request.status());
    }
}
