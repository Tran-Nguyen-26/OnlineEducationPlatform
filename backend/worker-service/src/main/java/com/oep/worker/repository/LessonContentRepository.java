package com.oep.worker.repository;

import com.oep.coremodel.entity.course.LessonContent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LessonContentRepository extends JpaRepository<LessonContent, UUID> {
}
