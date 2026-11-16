package com.oep.corelms.controller;

import com.oep.corelms.dto.social.SessionDtos;
import com.oep.corelms.service.AuditLogService;
import com.oep.corelms.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AuditLogService auditLogService;

    private static UUID uid(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private static String role(Jwt jwt) { return jwt.getClaimAsString("role"); }

    @GetMapping("/sessions")
    public List<SessionDtos.SessionResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return sessionService.list(courseId, uid(jwt), role(jwt));
    }

    @PostMapping("/sessions")
    public SessionDtos.SessionResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId,
                                               @Valid @RequestBody SessionDtos.CreateRequest request) {
        SessionDtos.SessionResponse r = sessionService.create(courseId, uid(jwt), role(jwt), request);
        auditLogService.record(uid(jwt), "CREATE_SESSION", "COURSE_SESSION", r.id(), Map.of("courseId", courseId.toString()));
        return r;
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @PathVariable UUID sessionId) {
        sessionService.delete(courseId, sessionId, uid(jwt), role(jwt));
        auditLogService.record(uid(jwt), "DELETE_SESSION", "COURSE_SESSION", sessionId, Map.of("courseId", courseId.toString()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{sessionId}/attendance")
    public List<SessionDtos.RosterRow> roster(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @PathVariable UUID sessionId) {
        return sessionService.roster(courseId, sessionId, uid(jwt), role(jwt));
    }

    @PutMapping("/sessions/{sessionId}/attendance")
    public Map<String, Integer> mark(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId, @PathVariable UUID sessionId,
                                      @Valid @RequestBody SessionDtos.MarkRequest request) {
        int saved = sessionService.mark(courseId, sessionId, uid(jwt), role(jwt), request);
        auditLogService.record(uid(jwt), "MARK_ATTENDANCE", "COURSE_SESSION", sessionId, Map.of("entries", String.valueOf(saved)));
        return Map.of("saved", saved);
    }

    @GetMapping("/attendance-summary")
    public List<SessionDtos.SummaryRow> summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID courseId) {
        return sessionService.summary(courseId, uid(jwt), role(jwt));
    }
}
