package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateSubjectRequest;
import com.oep.corelms.dto.admin.SubjectResponse;
import com.oep.corelms.dto.admin.UpdateSubjectRequest;
import com.oep.corelms.service.AdminSubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class AdminSubjectController {

    private final AdminSubjectService subjectService;

    @PostMapping
    public SubjectResponse create(@Valid @RequestBody CreateSubjectRequest request) {
        return subjectService.create(request);
    }

    @GetMapping
    public List<SubjectResponse> list() {
        return subjectService.list();
    }

    @PatchMapping("/{id}")
    public SubjectResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSubjectRequest request) {
        return subjectService.update(id, request);
    }
}
