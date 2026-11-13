package com.oep.corelms.controller;

import com.oep.corelms.repository.*;
import com.oep.coremodel.entity.assignment.Assignment;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/courses/{course}/admin-assignments")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAssignmentController {
    private final CourseAssignmentRepository assignments;
    private final CourseRepository courses;
    private final LearningOutcomeRepository outcomes;
    private final UserRepository users;

    public record Criterion(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 40) String outcome,
                            @NotBlank @Size(max = 500) String description, @NotNull @Positive @Digits(integer = 3, fraction = 2) BigDecimal points) {}
    public record Create(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 10000) String instructions,
                         @NotNull @Future Instant deadline, @Min(1) @Max(20) int attempts,
                         @NotEmpty @Size(max = 30) List<@NotNull @Valid Criterion> rubric) {}

    @PostMapping
    @Transactional
    public Map<String, UUID> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID course, @Valid @RequestBody Create input) {
        var curriculum = courses.findByIdForUpdate(course).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy khóa học"));
        if (curriculum.getLifecycleStatus() != CourseLifecycleStatus.DRAFT)
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Chỉ tạo bài tự luận trong bản nháp");
        var codes = new HashSet<String>();
        var rubric = new ArrayList<Assignment.RubricCriterion>();
        BigDecimal total = BigDecimal.ZERO;
        for (Criterion criterion : input.rubric()) {
            String outcome = criterion.outcome().strip().toUpperCase(Locale.ROOT);
            if (!codes.add(criterion.code().strip()) || !outcomes.existsByCourseIdAndCode(course, outcome))
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Mã tiêu chí trùng hoặc LO không thuộc khóa học");
            rubric.add(new Assignment.RubricCriterion(criterion.code().strip(), outcome, criterion.description().strip(), criterion.points()));
            total = total.add(criterion.points());
        }
        if (total.compareTo(new BigDecimal("999.99")) > 0)
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tổng điểm rubric quá lớn");
        var assignment = new Assignment();
        assignment.setCourse(curriculum);
        assignment.setCreatedBy(users.getReferenceById(UUID.fromString(jwt.getSubject())));
        assignment.setTitle(input.title().strip());
        assignment.setInstructions(input.instructions().strip());
        assignment.setDeadline(input.deadline());
        assignment.setMaxSubmissions(input.attempts());
        assignment.setMaxScore(total);
        assignment.setRubric(rubric);
        return Map.of("id", assignments.save(assignment).getId());
    }
}
