package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminDashboardStatsResponse;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.SubjectRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final SubjectRepository subjects;
    private final CourseRepository courses;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getStats() {
        long active = enrollments.countByStatus(EnrollmentStatus.ACTIVE);
        long completed = enrollments.countByStatus(EnrollmentStatus.COMPLETED);
        long withdrawn = enrollments.countByStatus(EnrollmentStatus.WITHDRAWN);
        long removed = enrollments.countByStatus(EnrollmentStatus.REMOVED);

        // Of enrollments that ever actually studied (active or completed), how many finished.
        long finishedOrOngoing = active + completed;
        double completionRate = finishedOrOngoing == 0 ? 0.0
                : Math.round(completed * 1000.0 / finishedOrOngoing) / 10.0;

        return new AdminDashboardStatsResponse(
                subjects.count(),
                courses.count(),
                courses.countByLifecycleStatus(CourseLifecycleStatus.DRAFT),
                courses.countByLifecycleStatus(CourseLifecycleStatus.PUBLISHED),
                courses.countByLifecycleStatus(CourseLifecycleStatus.ARCHIVED),
                courses.countByLifecycleStatus(CourseLifecycleStatus.DISABLED),
                users.countByRole(UserRole.INSTRUCTOR),
                users.countByRole(UserRole.STUDENT),
                active + completed + withdrawn + removed,
                active,
                completed,
                withdrawn,
                removed,
                completionRate);
    }
}
