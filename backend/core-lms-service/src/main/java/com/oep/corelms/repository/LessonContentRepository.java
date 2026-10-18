package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonContentRepository extends JpaRepository<LessonContent, UUID> {

    @Query("SELECT lc FROM LessonContent lc JOIN FETCH lc.lesson l JOIN FETCH l.module m JOIN FETCH m.course c WHERE lc.id = :id")
    Optional<LessonContent> findByIdWithCourse(@Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(lc.position), 0) FROM LessonContent lc WHERE lc.lesson.id = :lessonId")
    int findMaxPositionByLessonId(@Param("lessonId") UUID lessonId);

    List<LessonContent> findByUploadStatusAndCreatedAtBefore(UploadStatus uploadStatus, Instant threshold);
}
