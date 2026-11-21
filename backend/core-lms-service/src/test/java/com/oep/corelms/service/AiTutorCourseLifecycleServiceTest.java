package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CourseResponse;
import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.enums.CourseLifecycleStatus;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorCourseLifecycleServiceTest {
    private final AdminCourseService courses = mock(AdminCourseService.class);
    private final AiTutorAssertionIssuer issuer = mock(AiTutorAssertionIssuer.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final UUID courseId = UUID.randomUUID();
    private MockRestServiceServer server;
    private AiTutorCourseLifecycleService service;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(issuer.issueIndexer(any())).thenReturn("signed-index-token");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTutorCourseLifecycleService(courses, issuer, txManager,
                builder.baseUrl("http://ai-tutor.test").build());
    }

    private CourseResponse response(CourseLifecycleStatus lifecycle, long version) {
        CourseResponse response = mock(CourseResponse.class);
        when(response.lifecycleStatus()).thenReturn(lifecycle);
        when(response.version()).thenReturn(version);
        when(courses.updateStatus(courseId, lifecycle)).thenReturn(response);
        return response;
    }

    @Test
    void commitsPublicationBeforeSendingVersionedActivation() {
        CourseResponse expected = response(CourseLifecycleStatus.PUBLISHED, 5);
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/courses/" + courseId + "/access"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer signed-index-token"))
                .andExpect(content().json("{\"course_version\":5,\"active\":true}"))
                .andExpect(request -> verify(txManager).commit(any()))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertEquals(expected, service.updateStatus(courseId, CourseLifecycleStatus.PUBLISHED));
        server.verify();
    }

    @Test
    void leavesUnpublishedCourseUnavailableAndAllowsSameVersionRetry() {
        response(CourseLifecycleStatus.ARCHIVED, 8);
        String path = "http://ai-tutor.test/internal/v1/ai/courses/" + courseId + "/access";
        server.expect(requestTo(path))
                .andExpect(content().json("{\"course_version\":8,\"active\":false}"))
                .andExpect(request -> verify(txManager).commit(any()))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(courseId, CourseLifecycleStatus.ARCHIVED));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, error.getCode());
        server.verify();

        server.reset();
        server.expect(requestTo(path))
                .andExpect(content().json("{\"course_version\":8,\"active\":false}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        service.updateStatus(courseId, CourseLifecycleStatus.ARCHIVED);
        verify(courses, times(2)).updateStatus(courseId, CourseLifecycleStatus.ARCHIVED);
        server.verify();
    }
}
