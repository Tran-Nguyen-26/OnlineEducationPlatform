package com.oep.corelms.service;

import com.oep.corelms.dto.course.CourseDtos;
import com.oep.corelms.dto.course.LessonDtos;
import com.oep.corelms.dto.course.ModuleDtos;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseManagementService {
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final CourseInstructorRepository courseInstructors;
    private final UserRepository users;

    @Transactional
    public CourseDtos.CourseResponse createCourse(UUID actorId, CourseDtos.CreateCourseRequest request) {
        User actor = requireUser(actorId);
        if (actor.getRole() != UserRole.INSTRUCTOR) {
            throw new BusinessException(ErrorCode.INSTRUCTOR_ROLE_REQUIRED, "An approved instructor role is required");
        }
        validateDateRange(request.startAt(), request.endAt());
        Course course = new Course();
        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        course.setVisibility(request.visibility() == null ? CourseVisibility.PUBLIC : request.visibility());
        course.setCapacity(request.capacity());
        course.setLearningMode(request.learningMode() == null ? LearningMode.FREE_NAVIGATION : request.learningMode());
        course.setStartAt(request.startAt());
        course.setEndAt(request.endAt());
        course.setLifecycleStatus(CourseLifecycleStatus.DRAFT);
        course.setCreatedBy(actor);
        course = courses.saveAndFlush(course);

        CourseInstructor owner = new CourseInstructor();
        owner.setId(new CourseInstructorId(course.getId(), actorId));
        owner.setCourse(course);
        owner.setInstructor(actor);
        owner.setCourseRole(CourseInstructorRole.OWNER);
        owner.setAddedBy(actor);
        courseInstructors.save(owner);
        return toCourse(course);
    }

    @Transactional(readOnly = true)
    public CourseDtos.CoursePage listPublicCourses(int page, int pageSize, String search) {
        validatePage(page, pageSize);
        // Keep the parameter non-null so PostgreSQL can infer its type in the JPQL query.
        String normalizedSearch = search == null || search.isBlank() ? "" : search.trim();
        Page<Course> result = courses.findCatalog(CourseVisibility.PUBLIC, CourseLifecycleStatus.PUBLISHED,
                normalizedSearch, PageRequest.of(page - 1, pageSize));
        return new CourseDtos.CoursePage(result.getContent().stream().map(this::toCourse).toList(),
                new CourseDtos.PageInfo(page, pageSize, result.getTotalElements(), result.hasNext()));
    }

    @Transactional(readOnly = true)
    public CourseDtos.CoursePage listTeachingCourses(UUID actorId, int page, int pageSize) {
        validatePage(page, pageSize);
        Page<Course> result = courses.findManagedByInstructor(actorId, PageRequest.of(page - 1, pageSize));
        return new CourseDtos.CoursePage(result.getContent().stream().map(this::toCourse).toList(), pageInfo(result));
    }

    @Transactional(readOnly = true)
    public CourseDtos.CourseResponse getCourse(UUID actorId, UUID courseId) {
        Course course = requireCourse(courseId);
        if (course.getVisibility() == CourseVisibility.PUBLIC && course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED) {
            return toCourse(course);
        }
        requireCourseManager(courseId, actorId);
        return toCourse(course);
    }

    @Transactional
    public CourseDtos.CourseResponse updateCourse(UUID actorId, UUID courseId, CourseDtos.UpdateCourseRequest request) {
        Course course = requireCourse(courseId);
        requireCourseManager(courseId, actorId);
        if (course.getLifecycleStatus() == CourseLifecycleStatus.ARCHIVED || course.getLifecycleStatus() == CourseLifecycleStatus.DISABLED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Archived or disabled courses cannot be edited");
        }
        if (request.capacity() != null && !request.capacity().equals(course.getCapacity())) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "Capacity updates await the M3 seat-counting contract");
        }
        if (request.visibility() != null && request.visibility() != course.getVisibility()) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "Visibility changes await the M3 enrollment and invite-code contract");
        }
        if (request.title() == null && request.description() == null && request.learningMode() == null
                && request.startAt() == null && request.endAt() == null
                && request.capacity() == null && request.visibility() == null) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "At least one editable course field is required");
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "title must not be blank");
        }
        validateDateRange(request.startAt() != null ? request.startAt() : course.getStartAt(),
                request.endAt() != null ? request.endAt() : course.getEndAt());
        if (request.title() != null) course.setTitle(request.title().trim());
        if (request.description() != null) course.setDescription(request.description());
        if (request.learningMode() != null) course.setLearningMode(request.learningMode());
        if (request.startAt() != null) course.setStartAt(request.startAt());
        if (request.endAt() != null) course.setEndAt(request.endAt());
        return toCourse(courses.saveAndFlush(course));
    }

    @Transactional(readOnly = true)
    public ModuleDtos.PageResponse listModules(UUID actorId, UUID courseId, int page, int pageSize) {
        requireEditableCourse(courseId, actorId);
        validatePage(page, pageSize);
        Page<CourseModule> result = modules.findByCourseIdOrderByPositionAsc(courseId, PageRequest.of(page - 1, pageSize));
        return new ModuleDtos.PageResponse(result.getContent().stream().map(this::toModule).toList(), pageInfo(result));
    }

    @Transactional
    public ModuleDtos.Response createModule(UUID actorId, UUID courseId, ModuleDtos.CreateRequest request) {
        Course course = requireEditableCourse(courseId, actorId);
        int position = request.position() == null ? modules.findByCourseIdOrderByPositionAsc(courseId).size() : request.position();
        if (position < 0 || modules.existsByCourseIdAndPosition(courseId, position)) {
            throw new BusinessException(ErrorCode.INVALID_MODULE_POSITION, "Module position is already used or invalid");
        }
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault && modules.findByCourseIdOrderByPositionAsc(courseId).stream().anyMatch(CourseModule::isDefaultModule)) {
            throw new BusinessException(ErrorCode.INVALID_MODULE_POSITION, "A course can have at most one default module");
        }
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        module.setPosition(position);
        module.setDefaultModule(makeDefault);
        return toModule(modules.saveAndFlush(module));
    }

    @Transactional(readOnly = true)
    public ModuleDtos.Response getModule(UUID actorId, UUID courseId, UUID moduleId) {
        requireEditableCourse(courseId, actorId);
        return toModule(requireModule(courseId, moduleId));
    }

    @Transactional
    public ModuleDtos.Response updateModule(UUID actorId, UUID courseId, UUID moduleId, ModuleDtos.UpdateRequest request) {
        requireEditableCourse(courseId, actorId);
        CourseModule module = requireModule(courseId, moduleId);
        if (request.title() == null && request.description() == null && request.isDefault() == null) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "At least one module field is required");
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "title must not be blank");
        }
        if (request.title() != null) module.setTitle(request.title().trim());
        if (request.description() != null) module.setDescription(request.description());
        if (request.isDefault() != null && request.isDefault() && modules.existsByCourseIdAndDefaultModuleTrueAndIdNot(courseId, moduleId)) {
            throw new BusinessException(ErrorCode.INVALID_MODULE_POSITION, "A course can have at most one default module");
        }
        if (request.isDefault() != null) module.setDefaultModule(request.isDefault());
        return toModule(modules.saveAndFlush(module));
    }

    @Transactional(readOnly = true)
    public LessonDtos.PageResponse listLessons(UUID actorId, UUID courseId, UUID moduleId, int page, int pageSize) {
        requireEditableCourse(courseId, actorId);
        requireModule(courseId, moduleId);
        validatePage(page, pageSize);
        Page<Lesson> result = lessons.findByModuleIdOrderByPositionAsc(moduleId, PageRequest.of(page - 1, pageSize));
        return new LessonDtos.PageResponse(result.getContent().stream().map(this::toLesson).toList(), pageInfo(result));
    }

    @Transactional
    public LessonDtos.Response createLesson(UUID actorId, UUID courseId, UUID moduleId, LessonDtos.CreateRequest request) {
        requireEditableCourse(courseId, actorId);
        CourseModule module = requireModule(courseId, moduleId);
        int position = request.position() == null ? lessons.findByModuleIdOrderByPositionAsc(moduleId).size() : request.position();
        if (position < 0 || lessons.existsByModuleIdAndPosition(moduleId, position)) {
            throw new BusinessException(ErrorCode.INVALID_LESSON_POSITION, "Lesson position is already used or invalid");
        }
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle(request.title().trim());
        lesson.setDescription(request.description());
        lesson.setPosition(position);
        lesson.setRequired(request.isRequired() == null || request.isRequired());
        return toLesson(lessons.saveAndFlush(lesson));
    }

    @Transactional(readOnly = true)
    public LessonDtos.Response getLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId) {
        requireEditableCourse(courseId, actorId);
        return toLesson(requireLesson(moduleId, lessonId));
    }

    @Transactional
    public LessonDtos.Response updateLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId, LessonDtos.UpdateRequest request) {
        requireEditableCourse(courseId, actorId);
        Lesson lesson = requireLesson(moduleId, lessonId);
        if (request.title() == null && request.description() == null && request.isRequired() == null) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "At least one lesson field is required");
        }
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "title must not be blank");
        }
        if (request.title() != null) lesson.setTitle(request.title().trim());
        if (request.description() != null) lesson.setDescription(request.description());
        if (request.isRequired() != null) lesson.setRequired(request.isRequired());
        return toLesson(lessons.saveAndFlush(lesson));
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }
    private Course requireCourse(UUID id) {
        return courses.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
    }
    private Course requireEditableCourse(UUID courseId, UUID actorId) {
        Course course = requireCourse(courseId);
        requireCourseManager(courseId, actorId);
        if (course.getLifecycleStatus() == CourseLifecycleStatus.ARCHIVED || course.getLifecycleStatus() == CourseLifecycleStatus.DISABLED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Archived or disabled courses cannot be edited");
        }
        return course;
    }
    private CourseModule requireModule(UUID courseId, UUID moduleId) {
        return modules.findByIdAndCourseId(moduleId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_MODULE, "Module not found in course"));
    }
    private Lesson requireLesson(UUID moduleId, UUID lessonId) {
        return lessons.findByIdAndModuleId(lessonId, moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson not found in module"));
    }
    private void requireCourseManager(UUID courseId, UUID actorId) {
        if (actorId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        User actor = requireUser(actorId);
        if (actor.getRole() == UserRole.ADMIN) return;
        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required"));
        if (link.getCourseRole() != CourseInstructorRole.OWNER && link.getCourseRole() != CourseInstructorRole.CO_INSTRUCTOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        }
    }
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "page must be >= 1 and page_size must be between 1 and 100");
    }
    private void validateDateRange(Instant start, Instant end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessException(ErrorCode.INVALID_COURSE_METADATA, "end_at must be after start_at");
        }
    }
    private CourseDtos.PageInfo pageInfo(Page<?> p) { return new CourseDtos.PageInfo(p.getNumber() + 1, p.getSize(), p.getTotalElements(), p.hasNext()); }
    private CourseDtos.CourseResponse toCourse(Course c) {
        return new CourseDtos.CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getVisibility(), c.getLifecycleStatus(), c.getCapacity(), c.getLearningMode(), c.getStartAt(), c.getEndAt(), c.getCreatedBy().getId(), c.getCreatedAt(), c.getUpdatedAt());
    }
    private ModuleDtos.Response toModule(CourseModule m) { return new ModuleDtos.Response(m.getId(), m.getCourse().getId(), m.getTitle(), m.getDescription(), m.getPosition(), m.isDefaultModule(), m.getCreatedAt(), m.getUpdatedAt()); }
    private LessonDtos.Response toLesson(Lesson l) { return new LessonDtos.Response(l.getId(), l.getModule().getId(), l.getTitle(), l.getDescription(), l.getPosition(), l.isRequired(), l.getCreatedAt(), l.getUpdatedAt()); }
}
