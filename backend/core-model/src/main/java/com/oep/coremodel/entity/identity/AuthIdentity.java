package com.oep.coremodel.entity.identity;

import com.oep.coremodel.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.oep.coremodel.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "auth_identities", uniqueConstraints = {@UniqueConstraint(name = "uq_auth_identities_provider_provider_user_id", columnNames = {"provider", "provider_user_id"})}, indexes = {@Index(name = "idx_auth_identities_user_id", columnList = "user_id")})
public class AuthIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 40)
    private AuthProvider provider = AuthProvider.LOCAL;

    @Column(name = "provider_user_id", nullable = true, length = 255)
    private String providerUserId;

    @Column(name = "password_hash", nullable = true, length = 255)
    private String passwordHash;

    @Column(name = "email_verified_at", nullable = true)
    private Instant emailVerifiedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
