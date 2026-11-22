package com.oep.corelms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.oep.corelms.service.AiTutorAdminIndexService;
import com.oep.corelms.service.AiTutorIndexReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.Map;

/** Admin-only M2 management route; the browser never receives an M7 assertion. */
@RestController
@RequestMapping("/api/admin/courses/{courseId}/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AdminAiTutorController {
    private final AiTutorAdminIndexService index;
    private final AiTutorIndexReconciliationService reconciliation;

    @GetMapping("/index-status")
    public JsonNode indexStatus(@PathVariable UUID courseId) {
        return index.status(courseId);
    }

    @PostMapping("/reconcile")
    public Map<String, Integer> reconcile(@PathVariable UUID courseId) {
        return Map.of("enqueued", reconciliation.reconcile(courseId));
    }
}
