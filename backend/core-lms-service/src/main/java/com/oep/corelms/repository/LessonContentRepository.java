package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LessonContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonContentRepository extends JpaRepository<LessonContent, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from LessonContent c where c.id = :id")
    java.util.Optional<LessonContent> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<LessonContent> findByLesson_IdOrderByPositionAsc(UUID lessonId);

    long countByLesson_Id(UUID lessonId);

    java.util.Optional<LessonContent> findByIdAndLesson_Id(UUID id, UUID lessonId);

    List<LessonContent> findByLesson_Module_Course_Id(UUID courseId);
}
