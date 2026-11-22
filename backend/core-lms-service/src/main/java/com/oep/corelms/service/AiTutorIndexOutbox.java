package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.event.AiIndexRequestedEvent;
import com.oep.coremodel.event.OutboxEvents;
import com.oep.coremodel.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

/** Write AI indexing intent in the same M2 transaction as publication or content availability. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorIndexOutbox {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishIfEligible(LessonContent content) {
        publishIfEligible(content, UUID.randomUUID());
    }

    /** Stable event identity makes repeated recovery requests harmless at M7 intake. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean publishRecoveryIfEligible(LessonContent content) {
        UUID eventId = UUID.nameUUIDFromBytes(("ai-tutor-recovery:" + content.getId()
                + ":" + content.getVersionNumber()).getBytes(StandardCharsets.UTF_8));
        return publishIfEligible(content, eventId);
    }

    private boolean publishIfEligible(LessonContent content, UUID eventId) {
        if (content.getUploadStatus() != UploadStatus.AVAILABLE
                || content.getLesson().getModule().getCourse().getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED
                || !eligibleType(content)) return false;
        var event = new AiIndexRequestedEvent(eventId, content.getId(),
                content.getLesson().getModule().getCourse().getId(), content.getLesson().getId(),
                content.getVersionNumber(), "UPSERT");
        em.persist(OutboxEvents.aiIndex(event, mapper));
        return true;
    }

    static boolean eligibleType(LessonContent content) {
        if (content.getContentType() == ContentType.TEXT) return true;
        if (content.getContentType() != ContentType.PDF && content.getContentType() != ContentType.FILE) return false;
        try {
            return AiTutorLearningFilePolicy.maximumBytes(
                    content.getContentType(), content.getFileName(), content.getMimeType()) > 0;
        } catch (BusinessException ignored) {
            return false;
        }
    }
}
