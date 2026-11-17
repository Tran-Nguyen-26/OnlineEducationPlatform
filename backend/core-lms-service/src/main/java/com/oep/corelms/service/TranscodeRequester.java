package com.oep.corelms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.TranscodeStatus;
import com.oep.coremodel.event.OutboxEvents;
import com.oep.coremodel.event.VideoTranscodeRequestedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Asks worker-service (through the outbox, in the caller's transaction) to prepare an HLS version of a video. */
@Component
@RequiredArgsConstructor
public class TranscodeRequester {

    @PersistenceContext
    private EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean request(LessonContent content) {
        if (content.getContentType() != ContentType.VIDEO || content.getObjectKey() == null) return false;
        content.setTranscodeStatus(TranscodeStatus.PENDING);
        content.setHlsPrefix(null);
        em.persist(OutboxEvents.videoTranscode(new VideoTranscodeRequestedEvent(content.getId(), content.getObjectKey(), Instant.now()), mapper));
        return true;
    }
}
