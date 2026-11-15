package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateModuleRequest;
import com.oep.corelms.dto.admin.ModuleResponse;
import com.oep.corelms.dto.admin.UpdateModuleRequest;
import com.oep.corelms.service.AdminModuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminModuleController {

    private final AdminModuleService moduleService;

    @PostMapping("/api/admin/courses/{courseId}/modules")
    public ModuleResponse create(@PathVariable UUID courseId, @Valid @RequestBody CreateModuleRequest request) {
        return moduleService.create(courseId, request);
    }

    @GetMapping("/api/admin/courses/{courseId}/modules")
    public List<ModuleResponse> list(@PathVariable UUID courseId) {
        return moduleService.list(courseId);
    }

    @PatchMapping("/api/admin/modules/{moduleId}")
    public ModuleResponse update(@PathVariable UUID moduleId, @Valid @RequestBody UpdateModuleRequest request) {
        return moduleService.update(moduleId, request);
    }

    @DeleteMapping("/api/admin/modules/{moduleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID moduleId) {
        moduleService.delete(moduleId);
        return ResponseEntity.noContent().build();
    }
}
