package com.oep.corelms.repository;

import com.oep.coremodel.entity.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByCourse_IdOrderByCreatedAtDesc(UUID courseId);
}
