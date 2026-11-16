package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuestionBank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {
    Optional<QuestionBank> findFirstByCourse_IdOrderByCreatedAtAsc(UUID courseId);
}
