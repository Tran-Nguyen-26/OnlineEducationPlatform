package com.oep.corelms.service;

import com.oep.corelms.dto.admin.ReportDtos;
import com.oep.corelms.repository.AssignmentSubmissionRepository;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AssignmentStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.enums.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Admin analytics: completion/dropout per course and instructor, and a 6-month enrollment trend. */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final AssignmentSubmissionRepository submissions;

    @Transactional(readOnly = true)
    public ReportDtos.Overview overview() {
        List<Enrollment> all = enrollments.findAll();
        Map<UUID, List<Enrollment>> byCourse = all.stream().collect(Collectors.groupingBy(e -> e.getCourse().getId()));

        List<ReportDtos.CourseRow> courseRows = new ArrayList<>();
        for (Course c : courses.findAllByOrderByCreatedAtDesc()) {
            List<Enrollment> es = byCourse.getOrDefault(c.getId(), List.of());
            int active = count(es, EnrollmentStatus.ACTIVE), done = count(es, EnrollmentStatus.COMPLETED);
            int wd = count(es, EnrollmentStatus.WITHDRAWN), rm = count(es, EnrollmentStatus.REMOVED), wl = count(es, EnrollmentStatus.WAITLISTED);
            courseRows.add(new ReportDtos.CourseRow(c.getId(), c.getTitle(), c.getLifecycleStatus().name(), active, done, wd, rm, wl,
                    rate(done, active + done + wd + rm), rate(wd + rm, active + done + wd + rm)));
        }

        List<ReportDtos.InstructorRow> instructorRows = new ArrayList<>();
        for (User t : users.findByRoleInAndAccountStatus(EnumSet.of(UserRole.INSTRUCTOR), com.oep.coremodel.enums.AccountStatus.ACTIVE)) {
            List<UUID> ids = courseInstructors.findByInstructor_IdOrderByAddedAtDesc(t.getId()).stream()
                    .map(ci -> ci.getCourse().getId()).toList();
            int active = 0, done = 0, left = 0;
            for (UUID id : ids) {
                List<Enrollment> es = byCourse.getOrDefault(id, List.of());
                active += count(es, EnrollmentStatus.ACTIVE);
                done += count(es, EnrollmentStatus.COMPLETED);
                left += count(es, EnrollmentStatus.WITHDRAWN) + count(es, EnrollmentStatus.REMOVED);
            }
            int pending = ids.isEmpty() ? 0 : submissions.findByAssignment_Course_IdInAndStatusIn(ids,
                    EnumSet.of(AssignmentStatus.SUBMITTED, AssignmentStatus.PENDING_REGRADE)).size();
            UserProfile p = userProfiles.findById(t.getId()).orElse(null);
            instructorRows.add(new ReportDtos.InstructorRow(t.getId(), p != null ? p.getFullName() : t.getEmail(), t.getEmail(),
                    ids.size(), active, done, rate(done, active + done + left), pending));
        }

        YearMonth now = YearMonth.now(ZoneId.systemDefault());
        List<ReportDtos.MonthRow> months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth m = now.minusMonths(i);
            int enrolled = (int) all.stream().filter(e -> inMonth(e.getEnrolledAt(), m)).count();
            int completed = (int) all.stream().filter(e -> inMonth(e.getCompletedAt(), m)).count();
            months.add(new ReportDtos.MonthRow(m.toString(), enrolled, completed));
        }

        int active = count(all, EnrollmentStatus.ACTIVE), done = count(all, EnrollmentStatus.COMPLETED);
        int drop = count(all, EnrollmentStatus.WITHDRAWN) + count(all, EnrollmentStatus.REMOVED);
        int total = active + done + drop;
        return new ReportDtos.Overview(total, active, done, drop, rate(done, total), rate(drop, total), courseRows, instructorRows, months);
    }

    private static int count(List<Enrollment> es, EnrollmentStatus s) {
        return (int) es.stream().filter(e -> e.getStatus() == s).count();
    }

    private static Double rate(int num, int den) {
        return den == 0 ? null : Math.round(num * 1000.0 / den) / 10.0;
    }

    private static boolean inMonth(Instant t, YearMonth m) {
        return t != null && YearMonth.from(t.atZone(ZoneId.systemDefault())).equals(m);
    }
}
