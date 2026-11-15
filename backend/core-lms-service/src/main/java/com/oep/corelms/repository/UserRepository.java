package com.oep.corelms.repository;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByRole(UserRole role);
}
