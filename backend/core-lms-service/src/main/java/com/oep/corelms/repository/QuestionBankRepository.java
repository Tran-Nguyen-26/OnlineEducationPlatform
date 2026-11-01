package com.oep.corelms.repository;

import com.oep.coremodel.entity.quiz.QuestionBank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {

    Page<QuestionBank> findByCourseId(UUID courseId, Pageable pageable);

    Optional<QuestionBank> findByIdAndCourseId(UUID id, UUID courseId);
}
