package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID id,
        String fullName,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        String phone,
        String bio,
        String organization,
        LocalDate dateOfBirth,
        String avatarObjectKey,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminUserDetailResponse of(User user, UserProfile profile) {
        return new AdminUserDetailResponse(
                user.getId(),
                profile != null && profile.getFullName() != null ? profile.getFullName() : user.getEmail(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                profile != null ? profile.getPhone() : null,
                profile != null ? profile.getBio() : null,
                profile != null ? profile.getOrganization() : null,
                profile != null ? profile.getDateOfBirth() : null,
                profile != null ? profile.getAvatarObjectKey() : null,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
