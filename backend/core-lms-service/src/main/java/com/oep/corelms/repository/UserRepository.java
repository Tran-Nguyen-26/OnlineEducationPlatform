package com.oep.corelms.repository;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    long countByAccountStatus(AccountStatus accountStatus);

    @Query(value = """
        SELECT u, p FROM User u
        LEFT JOIN UserProfile p ON p.user = u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:accountStatus IS NULL OR u.accountStatus = :accountStatus)
          AND (
            CAST(:search AS string) IS NULL
            OR LOWER(u.email) LIKE CAST(:search AS string)
            OR (p.fullName IS NOT NULL AND LOWER(p.fullName) LIKE CAST(:search AS string))
          )
        """,
        countQuery = """
        SELECT COUNT(u) FROM User u
        LEFT JOIN UserProfile p ON p.user = u
        WHERE (:role IS NULL OR u.role = :role)
          AND (:accountStatus IS NULL OR u.accountStatus = :accountStatus)
          AND (
            CAST(:search AS string) IS NULL
            OR LOWER(u.email) LIKE CAST(:search AS string)
            OR (p.fullName IS NOT NULL AND LOWER(p.fullName) LIKE CAST(:search AS string))
          )
        """)
    Page<Object[]> findUsersWithProfile(
            @Param("search") String search,
            @Param("role") UserRole role,
            @Param("accountStatus") AccountStatus accountStatus,
            Pageable pageable
    );
}
