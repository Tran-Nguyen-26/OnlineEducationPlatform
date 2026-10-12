package com.oep.corelms.repository;

import com.oep.coremodel.entity.identity.AuthIdentity;
import com.oep.coremodel.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {

    Optional<AuthIdentity> findByUserIdAndProvider(UUID userId, AuthProvider provider);

    Optional<AuthIdentity> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
