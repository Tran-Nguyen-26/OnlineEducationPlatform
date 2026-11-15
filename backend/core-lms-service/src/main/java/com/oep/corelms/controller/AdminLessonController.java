package com.oep.corelms.controller;

import com.oep.corelms.dto.admin.CreateLessonRequest;
import com.oep.corelms.dto.admin.LessonResponse;
import com.oep.corelms.dto.admin.UpdateLessonRequest;
import com.oep.corelms.service.AdminLessonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminLessonController {

    private final AdminLessonService lessonService;

    @PostMapping("/api/admin/modules/{moduleId}/lessons")
    public LessonResponse create(@PathVariable UUID moduleId, @Valid @RequestBody CreateLessonRequest request) {
        return lessonService.create(moduleId, request);
    }

    @GetMapping("/api/admin/modules/{moduleId}/lessons")
    public List<LessonResponse> list(@PathVariable UUID moduleId) {
        return lessonService.list(moduleId);
    }

    @PatchMapping("/api/admin/lessons/{lessonId}")
    public LessonResponse update(@PathVariable UUID lessonId, @Valid @RequestBody UpdateLessonRequest request) {
        return lessonService.update(lessonId, request);
    }

    @DeleteMapping("/api/admin/lessons/{lessonId}")
    public ResponseEntity<Void> delete(@PathVariable UUID lessonId) {
        lessonService.delete(lessonId);
        return ResponseEntity.noContent().build();
    }
}
