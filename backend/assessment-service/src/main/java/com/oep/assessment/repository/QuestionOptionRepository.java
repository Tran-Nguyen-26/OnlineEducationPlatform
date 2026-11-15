package com.oep.assessment.repository;

import com.oep.coremodel.entity.quiz.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
    List<QuestionOption> findByQuestion_IdOrderByPositionAsc(UUID questionId);
    List<QuestionOption> findByQuestion_IdIn(List<UUID> questionIds);
}
