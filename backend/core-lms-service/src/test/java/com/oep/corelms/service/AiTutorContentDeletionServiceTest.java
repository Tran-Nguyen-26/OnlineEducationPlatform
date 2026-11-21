package com.oep.corelms.service;

import com.oep.corelms.repository.ContentChangeLogRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorContentDeletionServiceTest {
    private final LessonContentRepository contents = mock(LessonContentRepository.class);
    private final ContentChangeLogRepository logs = mock(ContentChangeLogRepository.class);
    private final AiTutorAssertionIssuer issuer = mock(AiTutorAssertionIssuer.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final RestClient.Builder builder = RestClient.builder();
    private MockRestServiceServer server;
    private AiTutorContentDeletionService service;
    private LessonContent content;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(issuer.issueIndexer(any())).thenReturn("signed-index-token");
        Course course = new Course();
        course.setId(UUID.randomUUID());
        CourseModule module = new CourseModule();
        module.setCourse(course);
        Lesson lesson = new Lesson();
        lesson.setId(UUID.randomUUID());
        lesson.setModule(module);
        content = new LessonContent();
        content.setId(UUID.randomUUID());
        content.setLesson(lesson);
        content.setUploadStatus(UploadStatus.AVAILABLE);
        content.setVersionNumber(3);
        when(contents.findById(content.getId())).thenReturn(Optional.of(content));
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTutorContentDeletionService(contents, logs, issuer, txManager,
                builder.baseUrl("http://ai-tutor.test").build());
    }

    @Test
    void hidesContentBeforeM7AckThenDeletesIt() {
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer signed-index-token"))
                .andExpect(request -> assertEquals(UploadStatus.DELETING, content.getUploadStatus()))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        service.delete(content.getId());

        server.verify();
        verify(logs).deleteByContent_Id(content.getId());
        verify(contents).delete(content);
    }

    @Test
    void keepsContentUnavailableForRetryWhenM7Fails() {
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/sources/" + content.getId() + "/deactivate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.delete(content.getId()));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
        assertEquals(UploadStatus.DELETING, content.getUploadStatus());
        verify(contents, never()).delete(content);
        server.verify();
    }
}
