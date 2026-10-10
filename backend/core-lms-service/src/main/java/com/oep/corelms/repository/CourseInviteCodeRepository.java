package com.oep.corelms.repository;

import com.oep.coremodel.entity.registration.CourseInviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseInviteCodeRepository extends JpaRepository<CourseInviteCode, UUID> {
    Optional<CourseInviteCode> findByCode(String code);
}
