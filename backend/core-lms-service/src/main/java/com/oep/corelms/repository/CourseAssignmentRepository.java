package com.oep.corelms.repository;

import com.oep.coremodel.entity.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CourseAssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByCourseIdOrderByCreatedAtAsc(UUID course);
    Optional<Assignment> findByIdAndCourseId(UUID id, UUID course);
}
