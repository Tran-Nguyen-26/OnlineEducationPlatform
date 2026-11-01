package com.oep.corelms.service;

import com.oep.corelms.dto.admin.AdminUserDetailResponse;
import com.oep.corelms.dto.admin.AdminUserItemResponse;
import com.oep.corelms.dto.admin.AdminUserStatsResponse;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public Page<AdminUserItemResponse> getUsers(
            String search,
            UserRole role,
            AccountStatus status,
            Pageable pageable
    ) {
        String searchPattern = (search != null && !search.trim().isEmpty())
                ? "%" + search.trim().toLowerCase() + "%"
                : null;
        Page<Object[]> results = userRepository.findUsersWithProfile(searchPattern, role, status, pageable);

        List<AdminUserItemResponse> list = new ArrayList<>();
        for (Object[] row : results.getContent()) {
            User user = (User) row[0];
            UserProfile profile = (UserProfile) row[1];
            list.add(AdminUserItemResponse.of(user, profile));
        }

        return new PageImpl<>(list, pageable, results.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminUserStatsResponse getUserStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByAccountStatus(AccountStatus.ACTIVE);
        long lockedUsers = userRepository.countByAccountStatus(AccountStatus.LOCKED)
                + userRepository.countByAccountStatus(AccountStatus.DISABLED);

        return new AdminUserStatsResponse(totalUsers, activeUsers, lockedUsers);
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng với ID: " + userId));

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        return AdminUserDetailResponse.of(user, profile);
    }

    @Transactional
    public AdminUserDetailResponse updateUserStatus(
            UUID targetUserId,
            AccountStatus newStatus,
            String reason,
            UUID currentAdminId
    ) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng với ID: " + targetUserId));

        // Safety check 1: Admin cannot lock themselves
        if (targetUserId.equals(currentAdminId) && (newStatus == AccountStatus.LOCKED || newStatus == AccountStatus.DISABLED)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không thể tự khóa tài khoản quản trị viên của chính mình.");
        }

        // Safety check 2: Prevent locking another admin account
        if (targetUser.getRole() == UserRole.ADMIN && (newStatus == AccountStatus.LOCKED || newStatus == AccountStatus.DISABLED)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được phép khóa tài khoản có vai trò Quản trị viên (ADMIN).");
        }

        targetUser.setAccountStatus(newStatus);
        userRepository.save(targetUser);

        log.info("Quản trị viên ({}) đã thay đổi trạng thái người dùng ({}, {}) sang: {}. Lý do: {}",
                currentAdminId, targetUser.getId(), targetUser.getEmail(), newStatus, reason);

        UserProfile profile = userProfileRepository.findById(targetUserId).orElse(null);
        return AdminUserDetailResponse.of(targetUser, profile);
    }
}
