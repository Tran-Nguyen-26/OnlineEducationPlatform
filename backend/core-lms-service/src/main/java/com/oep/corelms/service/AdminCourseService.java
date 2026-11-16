package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CourseResponse;
import com.oep.corelms.dto.admin.CreateCourseRequest;
import com.oep.corelms.dto.admin.UpdateCourseRequest;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.SubjectRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.Subject;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Admin creates/edits courses; only Admin ever writes course content (teachers are read-only, see course_instructors). */
@Service
@RequiredArgsConstructor
public class AdminCourseService {

    private final CourseRepository courses;
    private final SubjectRepository subjects;
    private final UserRepository users;

    @Transactional
    public CourseResponse create(UUID adminId, CreateCourseRequest request) {
        Subject subject = subjects.findById(request.subjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Subject not found"));

        Course course = new Course();
        course.setSubject(subject);
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setCapacity(request.capacity());
        course.setStartAt(request.startAt());
        course.setEndAt(request.endAt());
        course.setLearningMode(request.learningMode() == null ? LearningMode.FREE_NAVIGATION : request.learningMode());
        course.setFeeAmount(request.feeAmount());
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        course.setCreatedBy(users.getReferenceById(adminId));

        return CourseResponse.from(courses.saveAndFlush(course));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list() {
        return courses.findAllByOrderByCreatedAtDesc().stream().map(CourseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(UUID id) {
        return CourseResponse.from(findOrThrow(id));
    }

    @Transactional
    public CourseResponse update(UUID id, UpdateCourseRequest request) {
        Course course = findOrThrow(id);
        Subject subject = subjects.findById(request.subjectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Subject not found"));

        course.setSubject(subject);
        course.setTitle(request.title());
        course.setDescription(request.description());
        course.setCapacity(request.capacity());
        course.setStartAt(request.startAt());
        course.setEndAt(request.endAt());
        course.setLearningMode(request.learningMode());
        course.setFeeAmount(request.feeAmount());

        return CourseResponse.from(courses.saveAndFlush(course));
    }

    /** DRAFT is only ever the initial state; a published course is never sent back to DRAFT. */
    @Transactional
    public CourseResponse updateStatus(UUID id, CourseLifecycleStatus target) {
        Course course = findOrThrow(id);
        if (target == CourseLifecycleStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Cannot move a course back to DRAFT");
        }
        course.setLifecycleStatus(target);
        return CourseResponse.from(courses.saveAndFlush(course));
    }

    private Course findOrThrow(UUID id) {
        return courses.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
    }
}
