package com.oep.assessment.repository;

import com.oep.coremodel.entity.identity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
}
