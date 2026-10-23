package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.enums.UploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonContentRepository extends JpaRepository<LessonContent, UUID> {
    Page<LessonContent> findByLessonIdOrderByPositionAsc(UUID lessonId, Pageable pageable);
    Page<LessonContent> findByLessonIdAndUploadStatusOrderByPositionAsc(UUID lessonId, UploadStatus status, Pageable pageable);
    List<LessonContent> findByLessonIdOrderByPositionAsc(UUID lessonId);
    Optional<LessonContent> findByIdAndLessonId(UUID id, UUID lessonId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LessonContent c where c.id = :id and c.lesson.id = :lessonId")
    Optional<LessonContent> findByIdAndLessonIdForUpdate(@Param("id") UUID id, @Param("lessonId") UUID lessonId);
    boolean existsByLessonIdAndPosition(UUID lessonId, int position);

    @Query("select (count(c) > 0) from LessonContent c where c.lesson.module.course.id = :courseId and c.uploadStatus = :status")
    boolean existsAvailableInCourse(@Param("courseId") UUID courseId, @Param("status") UploadStatus status);
}
