package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CoursePrerequisiteResponse;
import com.oep.corelms.repository.CoursePrerequisiteRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CoursePrerequisite;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Điều kiện tiên quyết giữa khóa học (GAPS.md "Nhóm A"): courseId can't open for a student until every
 *  prerequisiteCourseId is COMPLETED by them — enforced in EnrollmentService, not here. */
@Service
@RequiredArgsConstructor
public class AdminCoursePrerequisiteService {

    private final CourseRepository courses;
    private final CoursePrerequisiteRepository prerequisites;

    @Transactional(readOnly = true)
    public List<CoursePrerequisiteResponse> list(UUID courseId) {
        return prerequisites.findByCourse_Id(courseId).stream()
                .map(p -> new CoursePrerequisiteResponse(p.getPrerequisiteCourse().getId(), p.getPrerequisiteCourse().getTitle()))
                .toList();
    }

    @Transactional
    public CoursePrerequisiteResponse add(UUID courseId, UUID prerequisiteCourseId) {
        if (courseId.equals(prerequisiteCourseId)) {
            throw new BusinessException(ErrorCode.CYCLIC_PREREQUISITE, "A course cannot be its own prerequisite");
        }
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        Course prerequisiteCourse = courses.findById(prerequisiteCourseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prerequisite course not found"));
        if (prerequisites.existsByCourse_IdAndPrerequisiteCourse_Id(courseId, prerequisiteCourseId)) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED, "This prerequisite is already set");
        }
        // Reject if prerequisiteCourseId already (transitively) depends on courseId — would form a cycle.
        if (isReachable(prerequisiteCourseId, courseId)) {
            throw new BusinessException(ErrorCode.CYCLIC_PREREQUISITE, "This would create a circular prerequisite chain");
        }

        CoursePrerequisite link = new CoursePrerequisite();
        link.setCourse(course);
        link.setPrerequisiteCourse(prerequisiteCourse);
        prerequisites.save(link);
        return new CoursePrerequisiteResponse(prerequisiteCourse.getId(), prerequisiteCourse.getTitle());
    }

    @Transactional
    public void remove(UUID courseId, UUID prerequisiteCourseId) {
        CoursePrerequisite link = prerequisites.findByCourse_IdAndPrerequisiteCourse_Id(courseId, prerequisiteCourseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Prerequisite not found"));
        prerequisites.delete(link);
    }

    /** BFS over the "requires" edges starting at `fromCourseId`: is `targetCourseId` reachable? */
    private boolean isReachable(UUID fromCourseId, UUID targetCourseId) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(fromCourseId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(targetCourseId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            prerequisites.findByCourse_Id(current).forEach(p -> queue.add(p.getPrerequisiteCourse().getId()));
        }
        return false;
    }
}
