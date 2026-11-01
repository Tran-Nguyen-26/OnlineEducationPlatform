package com.oep.corelms.dto.enrollment;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record EnrollRequest(@NotNull UUID courseId) {}