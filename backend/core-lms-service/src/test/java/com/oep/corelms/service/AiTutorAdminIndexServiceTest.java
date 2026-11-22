package com.oep.corelms.service;

import com.oep.corelms.security.AiTutorAssertionIssuer;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiTutorAdminIndexServiceTest {
    private final AdminCourseService courses = mock(AdminCourseService.class);
    private final AiTutorAssertionIssuer issuer = mock(AiTutorAssertionIssuer.class);
    private final RestClient.Builder builder = RestClient.builder();
    private final UUID courseId = UUID.randomUUID();
    private MockRestServiceServer server;
    private AiTutorAdminIndexService service;

    @BeforeEach
    void setUp() {
        when(issuer.issueIndexer(any())).thenReturn("signed-index-token");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AiTutorAdminIndexService(courses, issuer,
                builder.baseUrl("http://ai-tutor.test").build());
    }

    @Test
    void readsOnlyAnExistingCourseThroughThePrivateIndexAssertion() {
        server.expect(requestTo("http://ai-tutor.test/internal/v1/ai/courses/"
                        + courseId + "/index-status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer signed-index-token"))
                .andExpect(header("X-Request-Id", org.hamcrest.Matchers.notNullValue()))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"course_id\":\"" + courseId + "\",\"embedding_space_key\":\"test\","
                                + "\"items\":[{\"content_id\":\"" + UUID.randomUUID()
                                + "\",\"state\":\"READY\"}]}"));

        var body = service.status(courseId);
        assertEquals("READY", body.path("items").get(0).path("state").asText());
        verify(courses).get(courseId);
        server.verify();
    }

    @Test
    void failsClosedOnRemoteErrorOrWrongCourse() {
        String path = "http://ai-tutor.test/internal/v1/ai/courses/" + courseId + "/index-status";
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        BusinessException unavailable = assertThrows(BusinessException.class,
                () -> service.status(courseId));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, unavailable.getCode());
        server.verify();

        server.reset();
        server.expect(requestTo(path))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"course_id\":\"" + UUID.randomUUID() + "\",\"items\":[]}"));
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> service.status(courseId));
        assertEquals(ErrorCode.AI_TUTOR_UNAVAILABLE, wrong.getCode());
        server.verify();
    }
}
