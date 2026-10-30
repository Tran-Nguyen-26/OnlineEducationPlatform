package com.oep.corelms.controller;

import com.oep.corelms.config.SecurityConfig;
import com.oep.corelms.dto.enrollment.EnrollmentResponse;
import com.oep.corelms.service.*;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EnrollmentController.class)
@ContextConfiguration(classes = {EnrollmentController.class, SecurityConfig.class, JwtService.class,
        com.oep.corelms.exception.GlobalExceptionHandler.class})
class EnrollmentControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EnrollmentService service;
    UUID actor = UUID.randomUUID(), course = UUID.randomUUID(), id = UUID.randomUUID();
    EnrollmentResponse response() {
        return new EnrollmentResponse(id, course, actor, EnrollmentStatus.ACTIVE, "DIRECT", Instant.now(), null, null, null);
    }
    @Test void unauthenticated401() throws Exception {
        mvc.perform(post("/api/enrollments").contentType("application/json").content("{\"courseId\":\"" + course + "\"}"))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void usesJwtSubjectForStudent() throws Exception {
        when(service.enroll(course, actor)).thenReturn(response());
        mvc.perform(post("/api/enrollments").with(jwt().jwt(j -> j.subject(actor.toString())))
                .contentType("application/json").content("{\"courseId\":\"" + course + "\",\"studentId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(id.toString()));
        verify(service).enroll(course, actor);
    }
    @Test void missingCourse400() throws Exception {
        mvc.perform(post("/api/enrollments").with(jwt()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(service);
    }
    @Test void invalidUuid400() throws Exception {
        mvc.perform(post("/api/enrollments").with(jwt()).contentType("application/json").content("{\"courseId\":\"bad\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void malformedJson400() throws Exception {
        mvc.perform(post("/api/enrollments").with(jwt()).contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }
    @Test void invalidPath400() throws Exception {
        mvc.perform(post("/api/enrollments/bad/withdraw").with(jwt())).andExpect(status().isBadRequest());
    }
    @Test void malformedSubject401() throws Exception {
        mvc.perform(get("/api/enrollments").with(jwt().jwt(j -> j.subject("bad"))))
                .andExpect(status().isUnauthorized()); verifyNoInteractions(service);
    }
    @Test void full409() throws Exception {
        when(service.enroll(course, actor)).thenThrow(new BusinessException(ErrorCode.COURSE_FULL, "Course is full"));
        mvc.perform(post("/api/enrollments").with(jwt().jwt(j -> j.subject(actor.toString())))
                .contentType("application/json").content("{\"courseId\":\"" + course + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").value("COURSE_FULL"));
    }
    @Test void forbidden403() throws Exception {
        when(service.remove(id, actor)).thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "Owner required"));
        mvc.perform(post("/api/enrollments/" + id + "/remove").with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isForbidden());
    }
    @Test void withdrawEndpoint() throws Exception {
        when(service.withdraw(id, actor)).thenReturn(response());
        mvc.perform(post("/api/enrollments/" + id + "/withdraw").with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk()); verify(service).withdraw(id, actor);
    }
    @Test void listEndpointDefaults() throws Exception {
        when(service.list(actor, null, 0, 20)).thenReturn(Page.empty());
        mvc.perform(get("/api/enrollments").with(jwt().jwt(j -> j.subject(actor.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("content").isArray());
    }
    @Test void invalidPageType400() throws Exception {
        mvc.perform(get("/api/enrollments?page=bad").with(jwt())).andExpect(status().isBadRequest());
    }
}
