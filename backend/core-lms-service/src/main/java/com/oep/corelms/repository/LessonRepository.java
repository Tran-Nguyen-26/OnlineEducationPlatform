package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Lesson;
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

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Page<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId, Pageable pageable);
    List<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId);
    Optional<Lesson> findByIdAndModuleId(UUID id, UUID moduleId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lesson l where l.id = :id and l.module.id = :moduleId")
    Optional<Lesson> findByIdAndModuleIdForUpdate(@Param("id") UUID id, @Param("moduleId") UUID moduleId);
    boolean existsByModuleIdAndPosition(UUID moduleId, int position);

    @Query("select (count(l) > 0) from Lesson l where l.module.course.id = :courseId")
    boolean existsInCourse(@Param("courseId") UUID courseId);

}
