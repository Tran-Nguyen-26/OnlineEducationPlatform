package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CompleteUploadRequest;
import com.oep.corelms.dto.admin.RequestUploadUrlRequest;
import com.oep.corelms.dto.admin.UploadUrlResponse;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorFileReplacementServiceTest {
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final AdminLessonContentService legacy = mock(AdminLessonContentService.class);
    private final ContentChangeLogService logs = mock(ContentChangeLogService.class);
    private final AiTutorIndexOutbox outbox = mock(AiTutorIndexOutbox.class);
    private final AiTutorAssertionIssuer issuer = mock(AiTutorAssertionIssuer.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final UUID adminId = UUID.randomUUID();
    private final RestClient.Builder builder = RestClient.builder();
    private MockRestServiceServer server;
    private AiTutorFileReplacementService service;
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
        content.setContentType(ContentType.FILE);
        content.setFileName("old.md");
        content.setMimeType("text/markdown");
        content.setFileSize(100L);
        content.setObjectKey("private/old.md");
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.setVersionNumber(3);
        when(contents.findByIdForUpdate(content.getId())).thenReturn(Optional.of(content));
        when(legacy.requestUploadUrl(eq(content.getId()), any())).thenAnswer(call -> {
            content.setObjectKey("private/new.md");
            content.setUploadStatus(UploadStatus.PENDING);
            return new UploadUrlResponse("https://storage.test/upload", "private/new.md", 900);
        });
        when(legacy.completeUpload(eq(content.getId()), any())).thenAnswer(call -> {
            CompleteUploadRequest request = call.getArgument(1);
            content.setFileSize(request.fileSize());
            content.setUploadStatus(UploadStatus.AVAILABLE);
            return com.oep.corelms.dto.admin.LessonContentResponse.from(content);
        });
        doAnswer(call -> {
            content.setVersionNumber(content.getVersionNumber() + 1);
            return null;
        }).when(logs).record(content.getId(), adminId, "FILE_REPLACED");
        doAnswer(call -> {
            assertEquals(UploadStatus.AVAILABLE, content.getUploadStatus());
            assertEquals(4, content.getVersionNumber());
            return null;
        }).when(outbox).publishIfEligible(content);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTutorFileReplacementService(contents, legacy, logs, outbox,
                issuer, txManager, builder.baseUrl("http://ai-tutor.test").build());
    }

    @Test
    void replacementHidesOldFileBeforeGrantAndVersionsOnlyAfterCompletion() {
        server.expect(requestTo(deactivatePath()))
                .andExpect(request -> {
                    assertEquals(UploadStatus.INVALIDATING, content.getUploadStatus());
                    assertEquals("private/old.md", content.getObjectKey());
                    verify(legacy, never()).requestUploadUrl(any(), any());
                })
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        var grant = service.requestUploadUrl(content.getId(), fileRequest());
        assertEquals("private/new.md", grant.objectKey());
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
        assertEquals(3, content.getVersionNumber());
        verify(outbox, never()).publishIfEligible(any());

        var response = service.completeUpload(content.getId(), new CompleteUploadRequest(200L, null), adminId);
        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        assertEquals(4, content.getVersionNumber());
        verify(outbox).publishIfEligible(content);
        server.verify();
    }

    @Test
    void failedInvalidationLeavesOldFileHiddenAndRetryable() {
        server.expect(requestTo(deactivatePath())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(deactivatePath())).andRespond(withStatus(HttpStatus.NO_CONTENT));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requestUploadUrl(content.getId(), fileRequest()));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
        assertEquals(UploadStatus.INVALIDATING, content.getUploadStatus());
        assertEquals("private/old.md", content.getObjectKey());
        verify(legacy, never()).requestUploadUrl(any(), any());

        service.requestUploadUrl(content.getId(), fileRequest());
        assertEquals(UploadStatus.PENDING, content.getUploadStatus());
        server.verify();
    }

    @Test
    void initialUploadKeepsVersionOneAcrossJobAndHistory() {
        content.setUploadStatus(UploadStatus.PENDING);
        content.setFileSize(null);
        content.setVersionNumber(1);
        service.requestUploadUrl(content.getId(), fileRequest());
        var response = service.completeUpload(content.getId(), new CompleteUploadRequest(200L, null), adminId);

        assertEquals(UploadStatus.AVAILABLE, response.uploadStatus());
        assertEquals(1, content.getVersionNumber());
        verify(logs).recordCurrentVersion(content.getId(), adminId, "FILE_REPLACED");
        verify(logs, never()).record(any(), any(), any());
        verify(outbox, never()).publishIfEligible(any());
        verify(issuer, never()).issueIndexer(any());
    }

    @Test
    void completionWithoutNewGrantCannotReactivateOldFile() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.completeUpload(content.getId(), new CompleteUploadRequest(200L, null), adminId));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.getCode());
        verify(legacy, never()).completeUpload(any(), any());
    }

    @Test
    void uploadUrlForInlineTextCannotHideItsSource() {
        content.setContentType(ContentType.TEXT);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.requestUploadUrl(content.getId(), fileRequest()));
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, error.getCode());
        assertEquals(UploadStatus.AVAILABLE, content.getUploadStatus());
        verify(issuer, never()).issueIndexer(any());
    }

    private String deactivatePath() {
        return "http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate";
    }

    private static RequestUploadUrlRequest fileRequest() {
        return new RequestUploadUrlRequest("new.md", "text/markdown");
    }
}
