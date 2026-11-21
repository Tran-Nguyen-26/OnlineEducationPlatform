package com.oep.corelms.service;

import com.oep.corelms.dto.admin.LessonContentResponse;
import com.oep.corelms.dto.admin.UpdateLessonContentRequest;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorContentUpdateServiceTest {
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final AdminLessonContentService legacy = mock(AdminLessonContentService.class);
    private final ContentChangeLogService logs = mock(ContentChangeLogService.class);
    private final AiTutorIndexOutbox outbox = mock(AiTutorIndexOutbox.class);
    private final AiTutorAssertionIssuer issuer = mock(AiTutorAssertionIssuer.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final UUID adminId = UUID.randomUUID();
    private final RestClient.Builder builder = RestClient.builder();
    private MockRestServiceServer server;
    private AiTutorContentUpdateService service;
    private LessonContent content;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(issuer.issueIndexer(any())).thenReturn("signed-index-token");
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setLifecycleStatus(CourseLifecycleStatus.PUBLISHED);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setContentType(ContentType.TEXT);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.setTextContent("Old private lesson text");
        content.setVersionNumber(3);
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        doAnswer(call -> {
            content.setVersionNumber(content.getVersionNumber() + 1);
            return null;
        }).when(logs).record(content.getId(), adminId, "UPDATED");
        doAnswer(call -> {
            LessonContent queued = call.getArgument(0);
            assertEquals(UploadStatus.AVAILABLE, queued.getUploadStatus());
            assertEquals(4, queued.getVersionNumber());
            return null;
        }).when(outbox).publishIfEligible(content);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTutorContentUpdateService(contents, legacy, logs, outbox,
                issuer, txManager, builder.baseUrl("http://ai-tutor.test").build());
    }

    @Test
    void hidesOldTextBeforeAcknowledgementThenVersionsAndRequeues() {
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer signed-index-token"))
                .andExpect(request -> {
                    assertEquals(UploadStatus.INVALIDATING, content.getUploadStatus());
                    assertEquals("Old private lesson text", content.getTextContent());
                    assertEquals(3, content.getVersionNumber());
                    verify(outbox, never()).publishIfEligible(any());
                })
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        LessonContentResponse response = service.update(content.getId(), patch(), adminId);

        server.verify();
        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        assertEquals("New text", content.getTextContent());
        assertEquals(4, content.getVersionNumber());
        verify(outbox).publishIfEligible(content);
        verify(logs).record(content.getId(), adminId, "UPDATED");
        verify(legacy, never()).update(any(), any());
    }

    @Test
    void failedAcknowledgementLeavesOldRevisionHiddenForRetry() {
        String path = "http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate";
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.NO_CONTENT));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(content.getId(), patch(), adminId));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
        assertEquals(UploadStatus.INVALIDATING, content.getUploadStatus());
        assertEquals("Old private lesson text", content.getTextContent());
        verify(outbox, never()).publishIfEligible(any());

        service.update(content.getId(), patch(), adminId);
        server.verify();
        assertEquals(UploadStatus.AVAILABLE, content.getUploadStatus());
        assertEquals(4, content.getVersionNumber());
        verify(outbox).publishIfEligible(content);
    }

    @Test
    void rejectedTextNeverHidesAnOtherwiseReadableSource() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(content.getId(),
                        new UpdateLessonContentRequest("Title", 1, "  ", null), adminId));
        assertEquals(ErrorCode.VALIDATION_FAILED, error.getCode());
        assertEquals(UploadStatus.AVAILABLE, content.getUploadStatus());
        verify(outbox, never()).publishIfEligible(any());
    }

    @Test
    void unrelatedLinkUpdateDoesNotCallM7() {
        content.setContentType(ContentType.LINK);
        when(legacy.update(eq(content.getId()), any())).thenAnswer(call -> {
            content.setExternalUrl("https://example.test/new");
            return LessonContentResponse.from(content);
        });
        var response = service.update(content.getId(), patch(), adminId);
        assertEquals("https://example.test/new", response.externalUrl());
        assertEquals(4, content.getVersionNumber());
        verify(outbox, never()).publishIfEligible(any());
        verify(issuer, never()).issueIndexer(any());
    }

    @Test
    void archivedPdfMetadataStillInvalidatesPriorPublishedRevision() {
        content.setContentType(ContentType.PDF);
        content.setFileName("lecture.pdf");
        content.setMimeType("application/pdf");
        content.getLesson().getModule().getCourse()
                .setLifecycleStatus(CourseLifecycleStatus.ARCHIVED);
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.update(content.getId(), patch(), adminId);

        server.verify();
        assertEquals(UploadStatus.AVAILABLE, content.getUploadStatus());
        assertEquals(4, content.getVersionNumber());
        verify(outbox).publishIfEligible(content);
    }

    private static UpdateLessonContentRequest patch() {
        return new UpdateLessonContentRequest("New title", 2, "New text", null);
    }
}
