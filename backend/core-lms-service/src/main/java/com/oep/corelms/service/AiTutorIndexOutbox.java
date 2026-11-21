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

/** Write AI indexing intent in the same M2 transaction as publication or content availability. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "oep.ai-tutor", name = "enabled", havingValue = "true")
public class AiTutorIndexOutbox {
    @PersistenceContext private EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishIfEligible(LessonContent content) {
        if (content.getUploadStatus() != UploadStatus.AVAILABLE
                || content.getLesson().getModule().getCourse().getLifecycleStatus() != CourseLifecycleStatus.PUBLISHED
                || !eligibleType(content)) return;
        UUID eventId = UUID.randomUUID();
        var event = new AiIndexRequestedEvent(eventId, content.getId(),
                content.getLesson().getModule().getCourse().getId(), content.getLesson().getId(),
                content.getVersionNumber(), "UPSERT");
        em.persist(OutboxEvents.aiIndex(event, mapper));
    }

    private boolean eligibleType(LessonContent content) {
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
