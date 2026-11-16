package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
    java.util.List<Question> findByQuestionBank_IdOrderByCreatedAtDesc(UUID bankId);
}
