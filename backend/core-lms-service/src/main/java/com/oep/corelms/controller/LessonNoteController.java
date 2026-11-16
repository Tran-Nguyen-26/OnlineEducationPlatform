package com.oep.corelms.controller;

import com.oep.corelms.repository.LessonNoteRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.service.EnrollmentAccessService;
import com.oep.coremodel.entity.enrollment.LessonNote;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Private per-lesson notes; any student with read access to the course (active or completed) may keep notes. */
@RestController
@RequiredArgsConstructor
public class LessonNoteController {

    private static final int MAX_LENGTH = 20000;

    private final LessonNoteRepository notes;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final EnrollmentAccessService access;

    @GetMapping("/api/lessons/{lessonId}/note")
    @Transactional(readOnly = true)
    public Map<String, Object> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        requireAccess(lessonId, studentId);
        return notes.findByStudent_IdAndLesson_Id(studentId, lessonId)
                .map(n -> Map.<String, Object>of("content", n.getContent(), "updatedAt", n.getUpdatedAt()))
                .orElse(Map.of("content", ""));
    }

    @PutMapping("/api/lessons/{lessonId}/note")
    @Transactional
    public Map<String, Object> save(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID lessonId, @RequestBody Map<String, String> body) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        requireAccess(lessonId, studentId);
        String content = body.getOrDefault("content", "");
        if (content.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Note is too long");
        }
        LessonNote note = notes.findByStudent_IdAndLesson_Id(studentId, lessonId).orElseGet(() -> {
            LessonNote n = new LessonNote();
            n.setStudent(users.getReferenceById(studentId));
            n.setLesson(lessons.getReferenceById(lessonId));
            return n;
        });
        note.setContent(content);
        note.setUpdatedAt(Instant.now());
        notes.save(note);
        return Map.of("content", note.getContent(), "updatedAt", note.getUpdatedAt());
    }

    private void requireAccess(UUID lessonId, UUID studentId) {
        UUID courseId = lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"))
                .getModule().getCourse().getId();
        access.requireReadableEnrollment(courseId, studentId);
    }
}
