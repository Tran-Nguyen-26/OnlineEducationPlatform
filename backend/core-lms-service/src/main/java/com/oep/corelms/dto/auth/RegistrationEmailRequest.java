package com.oep.corelms.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegistrationEmailRequest(@NotBlank @Email String email) {}
