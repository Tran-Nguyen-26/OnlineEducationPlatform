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

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        List<PublicCourse> list = courses.findByLifecycleStatusOrderByCreatedAtDesc(CourseLifecycleStatus.PUBLISHED).stream()
                .map(this::toPublic)
                .filter(c -> c.lessonCount() > 0)
                .toList();
        int totalLessons = list.stream().mapToInt(PublicCourse::lessonCount).sum();
        int students = users.findByRoleAndAccountStatus(UserRole.STUDENT, AccountStatus.ACTIVE).size();
        int instructors = users.findByRoleAndAccountStatus(UserRole.INSTRUCTOR, AccountStatus.ACTIVE).size();
        return new Overview(new Stats(list.size(), totalLessons, students, instructors), list);
    }

    private PublicCourse toPublic(Course c) {
        int lessonCount = modules.findByCourse_IdOrderByPositionAsc(c.getId()).stream()
                .mapToInt(m -> (int) lessons.countByModule_Id(m.getId())).sum();
        return new PublicCourse(c.getId(), c.getTitle(), c.getDescription(), c.getSubject().getName(), c.getFeeAmount(),
                c.getLearningMode().name(), lessonCount);
    }
}
