package com.oep.corelms.repository;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByRole(UserRole role);

    List<User> findByRoleAndAccountStatus(UserRole role, AccountStatus accountStatus);

    List<User> findByRole(UserRole role);

    List<User> findByAccountStatus(AccountStatus accountStatus);

    long countByRole(UserRole role);

    List<User> findByRoleInAndAccountStatus(Collection<UserRole> roles, AccountStatus accountStatus);

    /** q must already be lower-cased and wrapped in % (use "%" for no text filter). */
    @Query("select u from User u left join UserProfile p on p.userId = u.id "
            + "where (:role is null or u.role = :role) and (:status is null or u.accountStatus = :status) "
            + "and (lower(u.email) like :q or lower(coalesce(p.fullName, '')) like :q)")
    Page<User> search(@Param("role") UserRole role, @Param("status") AccountStatus status, @Param("q") String q, Pageable pageable);
}
