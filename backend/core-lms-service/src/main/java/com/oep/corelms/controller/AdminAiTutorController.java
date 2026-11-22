package com.oep.corelms.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.oep.corelms.service.AiTutorAdminIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-only M2 management route; the browser never receives an M7 assertion. */
@RestController
@RequestMapping("/api/admin/courses/{courseId}/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AdminAiTutorController {
    private final AiTutorAdminIndexService index;

    @GetMapping("/index-status")
    public JsonNode indexStatus(@PathVariable UUID courseId) {
        return index.status(courseId);
    }
}
