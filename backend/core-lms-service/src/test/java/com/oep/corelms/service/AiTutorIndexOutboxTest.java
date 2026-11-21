package com.oep.corelms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiTutorIndexOutboxTest {

    @Test
    void writesVersionedIdentityWithoutSourceBytesOrStorageKey() throws Exception {
        EntityManager em = mock(EntityManager.class);
        ObjectMapper mapper = new ObjectMapper();
        AiTutorIndexOutbox outbox = new AiTutorIndexOutbox(mapper);
        ReflectionTestUtils.setField(outbox, "em", em);
        LessonContent content = source(ContentType.TEXT, UploadStatus.AVAILABLE);
        content.setTextContent("Private lesson text");
        content.setObjectKey("private/object-key");
        content.setVersionNumber(7);

        outbox.publishIfEligible(content);

        var capture = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(em).persist(capture.capture());
        OutboxEvent event = capture.getValue();
        assertEquals(MessagingConstants.RK_AI_INDEX, event.getEventType());
        assertEquals(content.getId(), event.getAggregateId());
        JsonNode payload = mapper.readTree(event.getPayload());
        assertNotNull(UUID.fromString(payload.get("event_id").asText()));
        assertEquals(content.getId().toString(), payload.get("content_id").asText());
        assertEquals(content.getLesson().getId().toString(), payload.get("lesson_id").asText());
        assertEquals(7, payload.get("version_number").asInt());
        assertEquals("UPSERT", payload.get("action").asText());
        assertEquals(false, event.getPayload().contains("Private lesson text"));
        assertEquals(false, event.getPayload().contains("private/object-key"));
    }

    @Test
    void skipsUnpublishedUnavailableAndUnsupportedSources() {
        EntityManager em = mock(EntityManager.class);
        AiTutorIndexOutbox outbox = new AiTutorIndexOutbox(new ObjectMapper());
        ReflectionTestUtils.setField(outbox, "em", em);
        LessonContent content = source(ContentType.TEXT, UploadStatus.PENDING);
        outbox.publishIfEligible(content);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.getLesson().getModule().getCourse().setLifecycleStatus(CourseLifecycleStatus.ARCHIVED);
        outbox.publishIfEligible(content);
        content.getLesson().getModule().getCourse().setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        content.setContentType(ContentType.VIDEO);
        outbox.publishIfEligible(content);
        content.setContentType(ContentType.FILE);
        content.setFileName("notes.bin");
        content.setMimeType("application/octet-stream");
        outbox.publishIfEligible(content);
        verify(em, never()).persist(any());
    }

    private static LessonContent source(ContentType type, UploadStatus status) {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        LessonContent content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(type);
        content.setUploadStatus(status);
        return content;
    }
}
