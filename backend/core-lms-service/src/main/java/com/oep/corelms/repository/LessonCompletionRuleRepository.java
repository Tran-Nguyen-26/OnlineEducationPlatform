package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.LessonCompletionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonCompletionRuleRepository extends JpaRepository<LessonCompletionRule, UUID> {
    Optional<LessonCompletionRule> findByLesson_Id(UUID lessonId);
}
