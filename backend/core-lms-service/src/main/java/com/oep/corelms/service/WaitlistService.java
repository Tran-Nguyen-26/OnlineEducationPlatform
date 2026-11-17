package com.oep.corelms.service;

import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Danh sách chờ (GAPS.md "Nhóm A"): when an ACTIVE seat frees up, auto-promote the longest-waiting WAITLISTED row. */
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final EventNotifier notifier;

    @Transactional
    public void promoteNext(UUID courseId) {
        Course course = courses.findByIdForUpdate(courseId).orElse(null);
        if (course == null || course.getCapacity() == null) {
            return; // unlimited capacity: nobody is ever waitlisted for this course
        }
        long activeCount = enrollments.countByCourseIdAndStatus(courseId, EnrollmentStatus.ACTIVE);
        if (activeCount >= course.getCapacity()) {
            return; // still full
        }
        enrollments.findFirstByCourseIdAndStatusOrderByEnrolledAtAsc(courseId, EnrollmentStatus.WAITLISTED)
                .ifPresent(this::promote);
    }

    private void promote(Enrollment enrollment) {
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollments.save(enrollment);
        notifier.send(enrollment.getStudent().getId(), "WAITLIST_PROMOTED", "Bạn đã có chỗ trong khóa học",
                "Khóa \"" + enrollment.getCourse().getTitle() + "\" vừa có chỗ trống — bạn đã được chuyển từ danh sách chờ vào lớp.",
                "waitlist:" + enrollment.getId() + ":" + System.currentTimeMillis(), enrollment.getCourse().getId());
    }
}
