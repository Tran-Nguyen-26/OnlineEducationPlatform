package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CreateModuleRequest;
import com.oep.corelms.dto.admin.ModuleResponse;
import com.oep.corelms.dto.admin.UpdateModuleRequest;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminModuleService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;

    @Transactional
    public ModuleResponse create(UUID courseId, CreateModuleRequest request) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Course not found"));
        CourseModule module = new CourseModule();
        module.setCourse(course);
        module.setTitle(request.title());
        module.setDescription(request.description());
        module.setPosition(request.position());
        return ModuleResponse.from(modules.saveAndFlush(module));
    }

    @Transactional(readOnly = true)
    public List<ModuleResponse> list(UUID courseId) {
        return modules.findByCourse_IdOrderByPositionAsc(courseId).stream().map(ModuleResponse::from).toList();
    }

    @Transactional
    public ModuleResponse update(UUID moduleId, UpdateModuleRequest request) {
        CourseModule module = findOrThrow(moduleId);
        module.setTitle(request.title());
        module.setDescription(request.description());
        module.setPosition(request.position());
        return ModuleResponse.from(modules.saveAndFlush(module));
    }

    @Transactional
    public void delete(UUID moduleId) {
        findOrThrow(moduleId);
        if (lessons.countByModule_Id(moduleId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Module still has lessons; remove them first");
        }
        modules.deleteById(moduleId);
    }

    private CourseModule findOrThrow(UUID moduleId) {
        return modules.findById(moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Module not found"));
    }
}
