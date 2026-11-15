package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModule_IdOrderByPositionAsc(UUID moduleId);

    long countByModule_Id(UUID moduleId);
}
