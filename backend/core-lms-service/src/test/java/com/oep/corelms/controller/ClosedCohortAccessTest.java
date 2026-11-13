package com.oep.corelms.controller;

import com.oep.corelms.config.SecurityConfig;
import com.oep.corelms.service.AuthService;
import com.oep.corelms.service.ForgotPasswordService;
import com.oep.corelms.service.InstructorApplicationService;
import com.oep.corelms.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, AdminInstructorApplicationController.class})
@ContextConfiguration(classes = {AuthController.class, AdminInstructorApplicationController.class,
        SecurityConfig.class, JwtService.class})
class ClosedCohortAccessTest {
    private static final String APPLICATION = """
            {"fullName":"Giang Vien","email":"instructor@example.com"}
            """;
    private static final String ADMIN_APPLICATIONS = "/api/admin/instructor-applications";

    @Autowired MockMvc mvc;
    @MockBean AuthService authService;
    @MockBean ForgotPasswordService forgotPasswordService;
    @MockBean InstructorApplicationService applications;

    @Test
    void publicInstructorRegistrationIsGone() throws Exception {
        mvc.perform(post("/api/auth/register-instructor").contentType("application/json").content(APPLICATION))
                .andExpect(status().isNotFound());
        verifyNoInteractions(applications);
    }

    @Test
    void anonymousUsersCannotReadCatalogOrCreateInstructorApplications() throws Exception {
        mvc.perform(get("/api/v1/courses")).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN_APPLICATIONS).contentType("application/json").content(APPLICATION))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(applications);
    }

    @Test
    void instructorCannotCreateInstructorApplications() throws Exception {
        mvc.perform(post(ADMIN_APPLICATIONS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                        .contentType("application/json").content(APPLICATION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(applications);
    }

    @Test
    void adminCanCreateInstructorApplications() throws Exception {
        mvc.perform(post(ADMIN_APPLICATIONS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType("application/json").content(APPLICATION))
                .andExpect(status().isCreated());
        verify(applications).submitApplication(argThat(request -> request.email().equals("instructor@example.com")));
    }
}
