package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Page<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId, Pageable pageable);
    List<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId);
    Optional<Lesson> findByIdAndModuleId(UUID id, UUID moduleId);
    boolean existsByModuleIdAndPosition(UUID moduleId, int position);
}
