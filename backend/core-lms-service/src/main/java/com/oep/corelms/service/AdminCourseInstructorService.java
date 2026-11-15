package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CourseInstructorResponse;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Admin assigns/unassigns teachers to a course; no hierarchy between teachers (see DATABASE.md). */
@Service
@RequiredArgsConstructor
public class AdminCourseInstructorService {

    private final CourseRepository courses;
    private final UserRepository users;
    private final UserProfileRepository userProfiles;
    private final CourseInstructorRepository courseInstructors;

    @Transactional
    public CourseInstructorResponse assign(UUID courseId, UUID instructorId, UUID adminId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        User instructor = users.findById(instructorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Instructor not found"));
        if (instructor.getRole() != UserRole.INSTRUCTOR) {
            throw new BusinessException(ErrorCode.NOT_AN_INSTRUCTOR, "User is not an instructor");
        }
        if (courseInstructors.existsByCourse_IdAndInstructor_Id(courseId, instructorId)) {
            throw new BusinessException(ErrorCode.ALREADY_ASSIGNED, "Instructor already assigned to this course");
        }

        CourseInstructor link = new CourseInstructor();
        link.setId(new CourseInstructorId(courseId, instructorId));
        link.setCourse(course);
        link.setInstructor(instructor);
        link.setAddedBy(users.getReferenceById(adminId));
        link = courseInstructors.save(link);

        return toResponse(link);
    }

    @Transactional(readOnly = true)
    public List<CourseInstructorResponse> list(UUID courseId) {
        return courseInstructors.findByCourse_Id(courseId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void unassign(UUID courseId, UUID instructorId) {
        long removed = courseInstructors.deleteByCourse_IdAndInstructor_Id(courseId, instructorId);
        if (removed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Instructor is not assigned to this course");
        }
    }

    private CourseInstructorResponse toResponse(CourseInstructor link) {
        User instructor = link.getInstructor();
        String fullName = userProfiles.findById(instructor.getId())
                .map(p -> p.getFullName())
                .orElse(null);
        return new CourseInstructorResponse(instructor.getId(), instructor.getEmail(), fullName, link.getAddedAt());
    }
}
