package com.oep.corelms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Compare current M2 content versions with M7's durable index state and resend gaps. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorIndexReconciliationService {
    private final AiTutorAdminIndexService index;
    private final CourseRepository courses;
    private final LessonContentRepository contents;
    private final AiTutorIndexOutbox outbox;

    @Transactional
    public int reconcile(UUID courseId) {
        JsonNode status = index.status(courseId);
        Map<UUID, Integer> indexed = new HashMap<>();
        for (JsonNode item : status.path("items")) {
            try {
                UUID contentId = UUID.fromString(item.path("content_id").asText());
                int version = item.path("version_number").intValue();
                if (version < 1 || indexed.putIfAbsent(contentId, version) != null) {
                    throw new IllegalArgumentException("Invalid or repeated source version");
                }
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(ErrorCode.AI_TUTOR_UNAVAILABLE,
                        "AI Tutor returned an invalid index status");
            }
        }
        var course = courses.findById(courseId).orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        if (course.getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED) return 0;

        int enqueued = 0;
        for (var content : contents.findByLesson_Module_Course_Id(courseId)) {
            if (indexed.getOrDefault(content.getId(), 0) < content.getVersionNumber()
                    && outbox.publishRecoveryIfEligible(content)) {
                enqueued++;
            }
        }
        return enqueued;
    }
}
