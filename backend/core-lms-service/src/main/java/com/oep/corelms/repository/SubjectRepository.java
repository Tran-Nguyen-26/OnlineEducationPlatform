package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    boolean existsByName(String name);

    List<Subject> findAllByOrderByNameAsc();
}
