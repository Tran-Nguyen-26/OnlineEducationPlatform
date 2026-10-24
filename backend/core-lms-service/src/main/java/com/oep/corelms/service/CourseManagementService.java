package com.oep.corelms.service;

import com.oep.corelms.dto.course.CourseDtos;
import com.oep.corelms.dto.course.LessonContentDtos;
import com.oep.corelms.dto.course.LessonDtos;
import com.oep.corelms.dto.course.ModuleDtos;
import com.oep.corelms.repository.CourseInstructorRepository;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonContentReferenceRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.corelms.storage.ObjectStorageService;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.system.AuditLog;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.CourseInstructorRole;
import com.oep.coremodel.enums.AuditAction;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import com.oep.coremodel.enums.ContentType;
import com.oep.coremodel.enums.LearningMode;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.EnrollmentStatus;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseManagementService {
    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonContentRepository contents;
    private final LessonContentReferenceRepository contentReferences;
    private final CourseInstructorRepository courseInstructors;
    private final AuditLogRepository auditLogs;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final ObjectStorageService objectStorage;

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

        CourseModule defaultModule = new CourseModule();
        defaultModule.setCourse(course);
        defaultModule.setTitle("Module 1: Introduction");
        defaultModule.setPosition(0);
        defaultModule.setDefaultModule(true);
        modules.save(defaultModule);
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

    @Transactional
    public CourseDtos.CourseResponse getCourse(UUID actorId, UUID courseId) {
        Course course = requireCourse(courseId);
        if (course.getVisibility() == CourseVisibility.PUBLIC && course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED) {
            return toCourse(course);
        }
        requireCourseReadAccess(course, actorId);
        return toCourse(course);
    }

    @Transactional
    public CourseDtos.CourseResponse deleteDraftCourse(UUID actorId, UUID courseId) {
        Course course = requireCourseForUpdate(courseId);
        CourseInstructor membership = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_OWNER_REQUIRED, "Course OWNER permission required"));
        if (membership.getCourseRole() == CourseInstructorRole.CO_INSTRUCTOR) {
            throw new BusinessException(ErrorCode.CO_INSTRUCTOR_CANNOT_DELETE_COURSE,
                    "Only the course OWNER may archive a draft course");
        }
        if (membership.getCourseRole() != CourseInstructorRole.OWNER) {
            throw new BusinessException(ErrorCode.COURSE_OWNER_REQUIRED, "Course OWNER permission required");
        }
        requireLifecycle(course, Set.of(CourseLifecycleStatus.DRAFT));
        return transitionCourse(course, actorId, CourseLifecycleStatus.ARCHIVED, AuditAction.ARCHIVE_COURSE);
    }

    @Transactional
    public CourseDtos.CourseOutline getCourseOutline(UUID actorId, UUID courseId) {
        Course course = requireCourse(courseId);
        boolean publicPreview = course.getVisibility() == CourseVisibility.PUBLIC
                && course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED;
        if (!publicPreview) {
            requireCourseReadAccess(course, actorId);
        }

        Map<UUID, List<LessonDtos.Response>> lessonsByModuleId = new HashMap<>();
        for (Lesson lesson : lessons.findAllForCourseOutline(courseId)) {
            lessonsByModuleId.computeIfAbsent(lesson.getModule().getId(), ignored -> new java.util.ArrayList<>())
                    .add(toLesson(lesson));
        }

        List<CourseDtos.ModuleOutline> outlineModules = modules.findByCourseIdOrderByPositionAsc(courseId).stream()
                .map(module -> new CourseDtos.ModuleOutline(toModule(module),
                        lessonsByModuleId.getOrDefault(module.getId(), List.of())))
                .toList();
        return new CourseDtos.CourseOutline(course.getId(), outlineModules);
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

    @Transactional
    public CourseDtos.CourseResponse publishCourse(UUID actorId, UUID courseId) {
        Course course = requireCourseForUpdate(courseId);
        requireCourseOwner(courseId, actorId);
        requireLifecycle(course, Set.of(CourseLifecycleStatus.DRAFT));
        if (!lessons.existsInCourse(courseId) || !contents.existsAvailableInCourse(courseId, UploadStatus.AVAILABLE)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_READY,
                    "A course needs at least one lesson and one available content item before publishing");
        }
        return transitionCourse(course, actorId, CourseLifecycleStatus.PUBLISHED, AuditAction.PUBLISH_COURSE);
    }

    @Transactional
    public CourseDtos.CourseResponse unpublishCourse(UUID actorId, UUID courseId) {
        return transitionOwnerCourse(actorId, courseId, Set.of(CourseLifecycleStatus.PUBLISHED), CourseLifecycleStatus.DRAFT);
    }

    @Transactional
    public CourseDtos.CourseResponse archiveCourse(UUID actorId, UUID courseId) {
        return transitionOwnerCourse(actorId, courseId,
                Set.of(CourseLifecycleStatus.DRAFT, CourseLifecycleStatus.PUBLISHED), CourseLifecycleStatus.ARCHIVED);
    }

    @Transactional
    public CourseDtos.CourseResponse restoreCourse(UUID actorId, UUID courseId) {
        return transitionOwnerCourse(actorId, courseId, Set.of(CourseLifecycleStatus.ARCHIVED), CourseLifecycleStatus.DRAFT);
    }

    @Transactional
    public CourseDtos.CourseResponse disableCourse(UUID actorId, UUID courseId) {
        Course course = requireCourseForUpdate(courseId);
        requireAdmin(actorId);
        requireLifecycle(course, Set.of(CourseLifecycleStatus.DRAFT, CourseLifecycleStatus.PUBLISHED));
        return transitionCourse(course, actorId, CourseLifecycleStatus.DISABLED, AuditAction.DISABLE_COURSE);
    }

    @Transactional
    public CourseDtos.CourseResponse enableCourse(UUID actorId, UUID courseId) {
        Course course = requireCourseForUpdate(courseId);
        requireAdmin(actorId);
        requireLifecycle(course, Set.of(CourseLifecycleStatus.DISABLED));
        return transitionCourse(course, actorId, CourseLifecycleStatus.DRAFT, AuditAction.ENABLE_COURSE);
    }

    @Transactional
    public CourseDtos.InstructorList listCourseInstructors(UUID actorId, UUID courseId) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
        List<CourseDtos.InstructorResponse> items = courseInstructors.findAllForCourse(courseId).stream()
                .map(link -> new CourseDtos.InstructorResponse(link.getInstructor().getId(), link.getCourseRole(), link.getAddedAt()))
                .toList();
        return new CourseDtos.InstructorList(items);
    }

    @Transactional
    public CourseDtos.InstructorResponse addCoInstructor(UUID actorId, UUID courseId, CourseDtos.AddInstructorRequest request) {
        Course course = requireCourseForUpdate(courseId);
        requireCourseOwner(courseId, actorId);
        User instructor = requireUser(request.instructorId());
        if (instructor.getRole() != UserRole.INSTRUCTOR || instructor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INSTRUCTOR_ROLE_REQUIRED, "An active instructor account is required");
        }
        CourseInstructorId id = new CourseInstructorId(courseId, instructor.getId());
        if (courseInstructors.existsById(id)) {
            throw new BusinessException(ErrorCode.COURSE_INSTRUCTOR_ALREADY_ASSIGNED, "Instructor is already assigned to this course");
        }
        CourseInstructor link = new CourseInstructor();
        link.setId(id);
        link.setCourse(course);
        link.setInstructor(instructor);
        link.setCourseRole(CourseInstructorRole.CO_INSTRUCTOR);
        link.setAddedBy(requireUser(actorId));
        CourseInstructor saved = courseInstructors.saveAndFlush(link);
        return new CourseDtos.InstructorResponse(instructor.getId(), saved.getCourseRole(), saved.getAddedAt());
    }

    @Transactional
    public CourseDtos.DeleteInstructorResponse removeCoInstructor(UUID actorId, UUID courseId, UUID instructorId) {
        requireCourseForUpdate(courseId);
        requireCourseOwner(courseId, actorId);
        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, instructorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course instructor not found"));
        if (link.getCourseRole() == CourseInstructorRole.OWNER) {
            throw new BusinessException(ErrorCode.CANNOT_REMOVE_OWNER, "The course owner cannot be removed");
        }
        courseInstructors.delete(link);
        return new CourseDtos.DeleteInstructorResponse(true, instructorId);
    }

    private CourseDtos.CourseResponse transitionOwnerCourse(UUID actorId, UUID courseId,
                                                              Set<CourseLifecycleStatus> allowed,
                                                              CourseLifecycleStatus target) {
        Course course = requireCourseForUpdate(courseId);
        requireCourseOwner(courseId, actorId);
        requireLifecycle(course, allowed);
        AuditAction action = switch (target) {
            case DRAFT -> course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED
                    ? AuditAction.UNPUBLISH_COURSE : AuditAction.RESTORE_COURSE;
            case ARCHIVED -> AuditAction.ARCHIVE_COURSE;
            default -> throw new IllegalArgumentException("Unsupported owner lifecycle target: " + target);
        };
        return transitionCourse(course, actorId, target, action);
    }

    private CourseDtos.CourseResponse transitionCourse(Course course, UUID actorId,
                                                        CourseLifecycleStatus target, AuditAction action) {
        CourseLifecycleStatus previous = course.getLifecycleStatus();
        course.setLifecycleStatus(target);
        Course saved = courses.saveAndFlush(course);
        AuditLog audit = new AuditLog();
        audit.setActor(users.getReferenceById(actorId));
        audit.setAction(action);
        audit.setTargetType("COURSE");
        audit.setTargetId(course.getId());
        audit.setMetadata("{\"from\":\"" + previous.name() + "\",\"to\":\"" + target.name() + "\"}");
        auditLogs.save(audit);
        return toCourse(saved);
    }

    private void requireLifecycle(Course course, Set<CourseLifecycleStatus> allowed) {
        if (!allowed.contains(course.getLifecycleStatus())) {
            throw new BusinessException(ErrorCode.INVALID_LIFECYCLE_TRANSITION,
                    "Cannot transition course from " + course.getLifecycleStatus());
        }
    }

    private void shiftModulesForInsertion(List<CourseModule> siblings, int position) {
        List<CourseModule> affected = siblings.stream().filter(module -> module.getPosition() >= position).toList();
        if (affected.isEmpty()) return;
        affected.forEach(module -> module.setPosition(-(module.getPosition() + 1)));
        modules.saveAllAndFlush(affected);
        affected.forEach(module -> module.setPosition(-module.getPosition()));
        modules.saveAllAndFlush(affected);
    }

    private void shiftLessonPositionsForInsertion(List<Lesson> siblings, int position) {
        List<Lesson> affected = siblings.stream().filter(lesson -> lesson.getPosition() >= position).toList();
        if (affected.isEmpty()) return;
        affected.forEach(lesson -> lesson.setPosition(-(lesson.getPosition() + 1)));
        lessons.saveAllAndFlush(affected);
        affected.forEach(lesson -> lesson.setPosition(-lesson.getPosition()));
        lessons.saveAllAndFlush(affected);
    }

    private void shiftContentPositionsForInsertion(List<LessonContent> siblings, int position) {
        List<LessonContent> affected = siblings.stream().filter(content -> content.getPosition() >= position).toList();
        if (affected.isEmpty()) return;
        affected.forEach(content -> content.setPosition(-(content.getPosition() + 1)));
        contents.saveAllAndFlush(affected);
        affected.forEach(content -> content.setPosition(-content.getPosition()));
        contents.saveAllAndFlush(affected);
    }

    private Course requireCourseForUpdate(UUID courseId) {
        return courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
    }

    private void requireCourseOwner(UUID courseId, UUID actorId) {
        if (actorId == null) throw new BusinessException(ErrorCode.COURSE_OWNER_REQUIRED, "Course OWNER permission required");
        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_OWNER_REQUIRED, "Course OWNER permission required"));
        if (link.getCourseRole() != CourseInstructorRole.OWNER) {
            throw new BusinessException(ErrorCode.COURSE_OWNER_REQUIRED, "Course OWNER permission required");
        }
    }

    private void requireAdmin(UUID actorId) {
        if (actorId == null || requireUser(actorId).getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.ADMIN_ROLE_REQUIRED, "ADMIN role required");
        }
    }

    @Transactional
    public ModuleDtos.PageResponse listModules(UUID actorId, UUID courseId, int page, int pageSize) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
        validatePage(page, pageSize);
        Page<CourseModule> result = modules.findByCourseIdOrderByPositionAsc(courseId, PageRequest.of(page - 1, pageSize));
        return new ModuleDtos.PageResponse(result.getContent().stream().map(this::toModule).toList(), pageInfo(result));
    }

    @Transactional
    public ModuleDtos.Response createModule(UUID actorId, UUID courseId, ModuleDtos.CreateRequest request) {
        requireEditableCourse(courseId, actorId);
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        ensureCourseEditable(course);
        List<CourseModule> siblings = modules.findByCourseIdOrderByPositionAsc(courseId);
        int position = request.position() == null ? siblings.size() : request.position();
        if (position < 0) {
            throw new BusinessException(ErrorCode.INVALID_MODULE_POSITION, "Module position must be non-negative");
        }
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault && siblings.stream().anyMatch(CourseModule::isDefaultModule)) {
            throw new BusinessException(ErrorCode.INVALID_MODULE_POSITION, "A course can have at most one default module");
        }
        shiftModulesForInsertion(siblings, position);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setTitle(request.title().trim());
        module.setDescription(request.description());
        module.setPosition(position);
        module.setDefaultModule(makeDefault);
        return toModule(modules.saveAndFlush(module));
    }

    @Transactional
    public ModuleDtos.Response getModule(UUID actorId, UUID courseId, UUID moduleId) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
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

    @Transactional
    public ModuleDtos.DeleteResponse deleteModule(UUID actorId, UUID courseId, UUID moduleId) {
        requireEditableCourse(courseId, actorId);
        CourseModule module = modules.findByIdAndCourseIdForUpdate(moduleId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_MODULE, "Module not found in course"));
        if (lessons.existsByModuleId(moduleId)) {
            throw new BusinessException(ErrorCode.MODULE_NOT_EMPTY, "A module with lessons cannot be deleted");
        }
        modules.delete(module);
        return new ModuleDtos.DeleteResponse(true, moduleId);
    }

    @Transactional
    public ModuleDtos.ReorderResponse reorderModules(UUID actorId, UUID courseId, ModuleDtos.ReorderRequest request) {
        requireInstructorCourseManager(courseId, actorId);
        Course course = courses.findByIdForUpdate(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        ensureCourseEditable(course);
        List<CourseModule> current = modules.findByCourseIdOrderByPositionAsc(courseId);
        List<UUID> orderedIds = request.orderedIds();
        validatePermutation(orderedIds, current.stream().map(m -> m.getId()).toList(), ErrorCode.INVALID_MODULE_ORDER);
        List<CourseModule> ordered = orderedIds.stream()
                .map(id -> current.stream().filter(m -> m.getId().equals(id)).findFirst().orElseThrow())
                .toList();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(-(i + 1));
        modules.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        modules.saveAllAndFlush(ordered);
        return new ModuleDtos.ReorderResponse(true, List.copyOf(orderedIds));
    }

    @Transactional
    public LessonDtos.PageResponse listLessons(UUID actorId, UUID courseId, UUID moduleId, int page, int pageSize) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
        requireModule(courseId, moduleId);
        validatePage(page, pageSize);
        Page<Lesson> result = lessons.findByModuleIdOrderByPositionAsc(moduleId, PageRequest.of(page - 1, pageSize));
        return new LessonDtos.PageResponse(result.getContent().stream().map(this::toLesson).toList(), pageInfo(result));
    }

    @Transactional
    public LessonDtos.Response createLesson(UUID actorId, UUID courseId, UUID moduleId, LessonDtos.CreateRequest request) {
        requireEditableCourse(courseId, actorId);
        CourseModule module = modules.findByIdAndCourseIdForUpdate(moduleId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_MODULE, "Module not found in course"));
        List<Lesson> siblings = lessons.findByModuleIdOrderByPositionAsc(moduleId);
        int position = request.position() == null ? siblings.size() : request.position();
        if (position < 0) {
            throw new BusinessException(ErrorCode.INVALID_LESSON_POSITION, "Lesson position must be non-negative");
        }
        shiftLessonPositionsForInsertion(siblings, position);
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle(request.title().trim());
        lesson.setDescription(request.description());
        lesson.setPosition(position);
        lesson.setRequired(request.isRequired() == null || request.isRequired());
        return toLesson(lessons.saveAndFlush(lesson));
    }

    @Transactional
    public LessonDtos.Response getLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
        requireModule(courseId, moduleId);
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

    @Transactional
    public LessonDtos.DeleteResponse deleteLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId) {
        Lesson lesson = requireEditableLesson(actorId, courseId, moduleId, lessonId);
        if (lessons.hasDeletionReferences(lessonId)) {
            throw new BusinessException(ErrorCode.LESSON_NOT_EMPTY,
                    "A lesson with content, completion rules, progress, quizzes or assignments cannot be deleted");
        }
        lessons.delete(lesson);
        return new LessonDtos.DeleteResponse(true, lessonId);
    }

    @Transactional
    public LessonContentDtos.PageResponse listLessonContents(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId,
                                                             int page, int pageSize, UploadStatus status) {
        Lesson lesson = requireReadableLesson(actorId, courseId, moduleId, lessonId);
        validatePage(page, pageSize);
        boolean manager = isCourseManager(courseId, actorId);
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        Page<LessonContent> result;
        if (!manager && status != null && status != UploadStatus.AVAILABLE) {
            result = Page.empty(pageable);
        } else {
            UploadStatus effectiveStatus = manager ? status : UploadStatus.AVAILABLE;
            result = effectiveStatus == null
                    ? contents.findByLessonIdOrderByPositionAsc(lesson.getId(), pageable)
                    : contents.findByLessonIdAndUploadStatusOrderByPositionAsc(lesson.getId(), effectiveStatus, pageable);
        }
        return new LessonContentDtos.PageResponse(result.getContent().stream().map(this::toContent).toList(), pageInfo(result));
    }

    @Transactional
    public LessonContentDtos.Response createLessonContent(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId,
                                                            LessonContentDtos.CreateRequest request) {
        Lesson lesson = requireEditableLesson(actorId, courseId, moduleId, lessonId);
        validateCreateContent(request);
        List<LessonContent> siblings = contents.findByLessonIdOrderByPositionAsc(lesson.getId());
        int position;
        if (request.position() == null) {
            position = siblings.isEmpty() ? 0 : siblings.get(siblings.size() - 1).getPosition() + 1;
        } else {
            position = request.position();
        }
        if (position < 0) {
            throw new BusinessException(ErrorCode.CONTENT_POSITION_TAKEN, "Content position must be non-negative");
        }
        shiftContentPositionsForInsertion(siblings, position);

        User actor = requireUser(actorId);
        LessonContent content = new LessonContent();
        content.setLesson(lesson);
        content.setContentType(request.contentType());
        content.setTitle(request.title());
        content.setTextContent(TextContentSanitizer.sanitize(request.textContent()));
        content.setExternalUrl(request.externalUrl());
        content.setFileName(request.fileName());
        content.setMimeType(request.mimeType());
        content.setFileSize(request.fileSize());
        content.setUploadStatus(isFileContent(request.contentType()) ? UploadStatus.PENDING : UploadStatus.AVAILABLE);
        content.setPosition(position);
        content.setCreatedBy(actor);
        content.setUpdatedBy(actor);
        return toContent(contents.saveAndFlush(content));
    }

    @Transactional
    public LessonContentDtos.Response getLessonContent(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId, UUID contentId) {
        Lesson lesson = requireReadableLesson(actorId, courseId, moduleId, lessonId);
        LessonContent content = requireContent(lesson.getId(), contentId);
        if (!isCourseManager(courseId, actorId) && content.getUploadStatus() != UploadStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT, "Content is not available");
        }
        return toContent(content);
    }

    @Transactional
    public LessonContentDtos.Response updateLessonContent(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId,
                                                            UUID contentId, LessonContentDtos.UpdateRequest request) {
        Lesson lesson = requireEditableLesson(actorId, courseId, moduleId, lessonId);
        LessonContent content = requireContent(lesson.getId(), contentId);
        requireTextOrLink(content);
        if (request.title() == null && request.textContent() == null && request.externalUrl() == null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "At least one content field is required");
        }
        if (content.getContentType() == ContentType.TEXT && request.externalUrl() != null
                || content.getContentType() == ContentType.LINK && request.textContent() != null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "Fields do not match the content type");
        }
        if (request.externalUrl() != null) validateExternalUrl(request.externalUrl());

        if (request.title() != null) content.setTitle(request.title());
        if (request.textContent() != null) content.setTextContent(TextContentSanitizer.sanitize(request.textContent()));
        if (request.externalUrl() != null) content.setExternalUrl(request.externalUrl());
        content.setUpdatedBy(requireUser(actorId));
        return toContent(contents.saveAndFlush(content));
    }

    @Transactional
    public LessonContentDtos.DeleteResponse deleteLessonContent(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId,
                                                                 UUID contentId) {
        Lesson lesson = requireEditableLesson(actorId, courseId, moduleId, lessonId);
        LessonContent content = contents.findByIdAndLessonIdForUpdate(contentId, lesson.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONTENT, "Content not found in lesson"));
        if (contentReferences.isReferenced(contentId)) {
            throw new BusinessException(ErrorCode.CONTENT_IN_USE, "Content is referenced by progress, AI data or change history");
        }
        if (isFileContent(content.getContentType()) && content.getObjectKey() != null) {
            objectStorage.delete(content.getObjectKey());
        } else {
            requireTextOrLink(content);
        }
        contents.delete(content);
        return new LessonContentDtos.DeleteResponse(true, contentId);
    }

    @Transactional
    public LessonDtos.ReorderResponse reorderLessons(UUID actorId, UUID courseId, UUID moduleId, LessonDtos.ReorderRequest request) {
        requireInstructorCourseManager(courseId, actorId);
        modules.findByIdAndCourseIdForUpdate(moduleId, courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_MODULE, "Module not found in course"));
        List<Lesson> current = lessons.findByModuleIdOrderByPositionAsc(moduleId);
        List<UUID> orderedIds = request.orderedIds();
        validatePermutation(orderedIds, current.stream().map(l -> l.getId()).toList(), ErrorCode.INVALID_LESSON_ORDER);
        List<Lesson> ordered = orderedIds.stream()
                .map(id -> current.stream().filter(l -> l.getId().equals(id)).findFirst().orElseThrow())
                .toList();
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(-(i + 1));
        lessons.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        lessons.saveAllAndFlush(ordered);
        return new LessonDtos.ReorderResponse(true, List.copyOf(orderedIds));
    }

    private void validatePermutation(List<UUID> requested, List<UUID> existing, ErrorCode errorCode) {
        if (requested == null || requested.size() != existing.size()
                || new HashSet<>(requested).size() != requested.size()
                || !new HashSet<>(requested).equals(new HashSet<>(existing))) {
            throw new BusinessException(errorCode, "ordered_ids must contain every direct child exactly once");
        }
    }

    private void ensureCourseEditable(Course course) {
        if (course.getLifecycleStatus() == CourseLifecycleStatus.ARCHIVED || course.getLifecycleStatus() == CourseLifecycleStatus.DISABLED) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE, "Archived or disabled courses cannot be edited");
        }
    }

    private void requireInstructorCourseManager(UUID courseId, UUID actorId) {
        requireInstructorAssociation(courseId, actorId);
        Course course = requireCourse(courseId);
        ensureCourseEditable(course);
    }

    private void requireInstructorAssociation(UUID courseId, UUID actorId) {
        if (actorId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        requireCourse(courseId);
        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required"));
        if (link.getCourseRole() != CourseInstructorRole.OWNER && link.getCourseRole() != CourseInstructorRole.CO_INSTRUCTOR) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Course manager access is required");
        }
    }

    private Lesson requireReadableLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
        requireModule(courseId, moduleId);
        return lessons.findByIdAndModuleId(lessonId, moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson not found in module"));
    }

    private Lesson requireEditableLesson(UUID actorId, UUID courseId, UUID moduleId, UUID lessonId) {
        requireInstructorCourseManager(courseId, actorId);
        requireModule(courseId, moduleId);
        return lessons.findByIdAndModuleIdForUpdate(lessonId, moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_COURSE_LESSON, "Lesson not found in module"));
    }

    private LessonContent requireContent(UUID lessonId, UUID contentId) {
        return contents.findByIdAndLessonId(contentId, lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONTENT, "Content not found in lesson"));
    }

    private void validateCreateContent(LessonContentDtos.CreateRequest request) {
        if (isFileContent(request.contentType())) {
            if (request.textContent() != null || request.externalUrl() != null
                    || request.fileName() == null || request.fileName().isBlank()
                    || request.mimeType() == null || request.fileSize() == null) {
                throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA,
                        "File content requires file_name, mime_type and file_size and cannot include text or external URL");
            }
            if (!FileUploadPolicy.isAllowed(request.contentType(), request.mimeType())) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "mime_type is not supported for this file content type");
            }
            if (request.fileSize() > FileUploadPolicy.MAX_FILE_SIZE_BYTES) {
                throw new BusinessException(ErrorCode.UPLOAD_TOO_LARGE, "Maximum file size is 100 MiB");
            }
            return;
        }
        if (request.contentType() != ContentType.TEXT && request.contentType() != ContentType.LINK) {
            throw new BusinessException(ErrorCode.CONTENT_TYPE_NOT_SUPPORTED, "Unsupported content type");
        }
        if (request.fileName() != null || request.mimeType() != null || request.fileSize() != null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "File metadata is not accepted for TEXT or LINK content");
        }
        if (request.contentType() == ContentType.TEXT) {
            if (request.textContent() == null || request.externalUrl() != null) {
                throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "TEXT requires text_content and cannot include external_url");
            }
        } else if (request.externalUrl() == null || request.textContent() != null) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "LINK requires external_url and cannot include text_content");
        } else {
            validateExternalUrl(request.externalUrl());
        }
    }

    private void validateExternalUrl(String value) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new URISyntaxException(value, "Only absolute HTTP(S) links are allowed");
            }
        } catch (URISyntaxException ex) {
            throw new BusinessException(ErrorCode.INVALID_CONTENT_METADATA, "external_url must be a valid absolute HTTP(S) URL");
        }
    }

    private void requireTextOrLink(LessonContent content) {
        if (content.getContentType() != ContentType.TEXT && content.getContentType() != ContentType.LINK) {
            throw new BusinessException(ErrorCode.CONTENT_TYPE_NOT_SUPPORTED, "File metadata can only be changed through the upload flow");
        }
    }

    public void assertCourseManagerAccess(UUID courseId, UUID actorId) {
        requireInstructorAssociation(courseId, actorId);
    }

    public void assertCourseReadAccess(UUID courseId, UUID actorId) {
        requireCourseReadAccess(requireCourse(courseId), actorId);
    }

    private boolean isFileContent(ContentType type) {
        return FileUploadPolicy.isFileContent(type);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }
    private Course requireCourse(UUID id) {
        return courses.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
    }

    private void requireCourseReadAccess(Course course, UUID actorId) {
        if (actorId == null) throw new BusinessException(ErrorCode.FORBIDDEN, "Published course access requires enrollment");
        User actor = requireUser(actorId);
        if (actor.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Active account required");
        }
        if (actor.getRole() == UserRole.ADMIN) return;

        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(course.getId(), actorId).orElse(null);
        if (link != null && (link.getCourseRole() == CourseInstructorRole.OWNER
                || link.getCourseRole() == CourseInstructorRole.CO_INSTRUCTOR)) return;

        boolean activeStudent = actor.getRole() == UserRole.STUDENT
                && course.getLifecycleStatus() == CourseLifecycleStatus.PUBLISHED
                && enrollments.existsByCourseIdAndStudentIdAndStatus(course.getId(), actorId, EnrollmentStatus.ACTIVE);
        if (!activeStudent) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Published course access requires ACTIVE enrollment");
        }
    }

    private boolean isCourseManager(UUID courseId, UUID actorId) {
        if (actorId == null) return false;
        User actor = requireUser(actorId);
        if (actor.getRole() == UserRole.ADMIN) return true;
        CourseInstructor link = courseInstructors.findByIdCourseIdAndIdInstructorId(courseId, actorId).orElse(null);
        return link != null && (link.getCourseRole() == CourseInstructorRole.OWNER
                || link.getCourseRole() == CourseInstructorRole.CO_INSTRUCTOR);
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
    private LessonContentDtos.Response toContent(LessonContent c) {
        return new LessonContentDtos.Response(c.getId(), c.getLesson().getId(), c.getContentType(), c.getTitle(),
                TextContentSanitizer.sanitize(c.getTextContent()), c.getExternalUrl(), c.getFileName(), c.getMimeType(), c.getFileSize(),
                c.getUploadStatus(), c.getPosition(), c.getVersionNumber(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
