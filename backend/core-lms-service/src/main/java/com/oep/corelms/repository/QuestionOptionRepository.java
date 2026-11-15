package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {
    List<QuestionOption> findByQuestion_IdOrderByPositionAsc(UUID questionId);

    void deleteByQuestion_Id(UUID questionId);
}
