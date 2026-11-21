package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LessonContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonContentRepository extends JpaRepository<LessonContent, UUID> {
    List<LessonContent> findByLesson_IdOrderByPositionAsc(UUID lessonId);

    long countByLesson_Id(UUID lessonId);

    java.util.Optional<LessonContent> findByIdAndLesson_Id(UUID id, UUID lessonId);

    List<LessonContent> findByLesson_Module_Course_Id(UUID courseId);
}
