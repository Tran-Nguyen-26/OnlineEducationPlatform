package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionIdOrderByPositionAsc(UUID questionId);

    void deleteByQuestionId(UUID questionId);
}
