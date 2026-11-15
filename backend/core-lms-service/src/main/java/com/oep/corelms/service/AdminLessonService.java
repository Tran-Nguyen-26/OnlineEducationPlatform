package com.oep.corelms.service;

import com.oep.corelms.dto.admin.CreateLessonRequest;
import com.oep.corelms.dto.admin.LessonResponse;
import com.oep.corelms.dto.admin.UpdateLessonRequest;
import com.oep.corelms.repository.CourseModuleRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.LessonRepository;
import com.oep.coremodel.entity.course.CourseModule;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminLessonService {

    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonContentRepository lessonContents;

    @Transactional
    public LessonResponse create(UUID moduleId, CreateLessonRequest request) {
        CourseModule module = modules.findById(moduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Module not found"));
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        lesson.setTitle(request.title());
        lesson.setDescription(request.description());
        lesson.setPosition(request.position());
        lesson.setRequired(request.required());
        return LessonResponse.from(lessons.saveAndFlush(lesson));
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> list(UUID moduleId) {
        return lessons.findByModule_IdOrderByPositionAsc(moduleId).stream().map(LessonResponse::from).toList();
    }

    @Transactional
    public LessonResponse update(UUID lessonId, UpdateLessonRequest request) {
        Lesson lesson = findOrThrow(lessonId);
        lesson.setTitle(request.title());
        lesson.setDescription(request.description());
        lesson.setPosition(request.position());
        lesson.setRequired(request.required());
        return LessonResponse.from(lessons.saveAndFlush(lesson));
    }

    @Transactional
    public void delete(UUID lessonId) {
        findOrThrow(lessonId);
        if (lessonContents.countByLesson_Id(lessonId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Lesson still has content; remove it first");
        }
        lessons.deleteById(lessonId);
    }

    private Lesson findOrThrow(UUID lessonId) {
        return lessons.findById(lessonId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Lesson not found"));
    }
}
