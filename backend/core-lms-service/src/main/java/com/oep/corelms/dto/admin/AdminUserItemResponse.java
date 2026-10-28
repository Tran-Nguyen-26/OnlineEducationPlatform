package com.oep.corelms.dto.admin;

import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AdminUserItemResponse(
        UUID id,
        String fullName,
        String email,
        UserRole role,
        AccountStatus accountStatus,
        Instant createdAt,
        String phone,
        String organization,
        String avatarObjectKey
) {
    public static AdminUserItemResponse of(User user, UserProfile profile) {
        return new AdminUserItemResponse(
                user.getId(),
                profile != null && profile.getFullName() != null ? profile.getFullName() : user.getEmail(),
                user.getEmail(),
                user.getRole(),
                user.getAccountStatus(),
                user.getCreatedAt(),
                profile != null ? profile.getPhone() : null,
                profile != null ? profile.getOrganization() : null,
                profile != null ? profile.getAvatarObjectKey() : null
        );
    }
}
