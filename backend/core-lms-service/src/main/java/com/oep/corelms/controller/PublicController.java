package com.oep.corelms.controller;

import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Unauthenticated data for the marketing landing page: published courses that already have content, plus headline numbers. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    public record PublicCourse(UUID id, String title, String description, String subjectName, BigDecimal feeAmount,
                               String learningMode, int lessonCount) {}

    public record Stats(int courses, int lessons, int students, int instructors) {}

    public record Overview(Stats stats, List<PublicCourse> courses) {}

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final UserRepository users;
    private final com.oep.corelms.service.CacheService cache;

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        // landing page is hit by every anonymous visitor: serve it from Redis for 60 s
        return cache.get("public-overview", Overview.class, java.time.Duration.ofSeconds(60), this::loadOverview);
    }

    private Overview loadOverview() {
        List<Course> published = courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED);
        java.util.Map<UUID, Integer> lessonCounts = new java.util.HashMap<>();
        if (!published.isEmpty()) {
            for (Object[] row : lessons.countByCourseIds(published.stream().map(Course::getId).toList())) {
                lessonCounts.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }
        List<PublicCourse> list = published.stream()
                .map(c -> toPublic(c, lessonCounts.getOrDefault(c.getId(), 0)))
                .filter(c -> c.lessonCount() > 0)
                .toList();
        int totalLessons = list.stream().mapToInt(PublicCourse::lessonCount).sum();
        int students = (int) users.countByRoleAndAccountStatus(UserRole.STUDENT, AccountStatus.ACTIVE);
        int instructors = (int) users.countByRoleAndAccountStatus(UserRole.INSTRUCTOR, AccountStatus.ACTIVE);
        return new Overview(new Stats(list.size(), totalLessons, students, instructors), list);
    }

    private PublicCourse toPublic(Course c, int lessonCount) {
        return new PublicCourse(c.getId(), c.getTitle(), c.getDescription(), c.getSubject().getName(), c.getFeeAmount(),
                c.getLearningMode().name(), lessonCount);
    }
}
