package com.oep.corelms.service;

import com.oep.corelms.dto.student.MyCertificateResponse;
import com.oep.corelms.dto.student.ProfileResponse;
import com.oep.corelms.dto.student.UpdateProfileRequest;
import com.oep.corelms.repository.CertificateRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Self-service profile for any logged-in user, plus the student's own certificate list. */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final CertificateRepository certificates;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public ProfileResponse get(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        return toResponse(user, profiles.findById(userId).orElse(null));
    }

    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserProfile profile = profiles.findById(userId).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setUser(user);
            return p;
        });
        profile.setFullName(request.fullName().trim());
        profile.setPhone(blankToNull(request.phone()));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setOrganization(blankToNull(request.organization()));
        profile.setBio(blankToNull(request.bio()));
        return toResponse(user, profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public List<MyCertificateResponse> myCertificates(UUID studentId) {
        return certificates.findByEnrollment_Student_IdOrderByIssuedAtDesc(studentId).stream()
                .map(c -> new MyCertificateResponse(c.getEnrollment().getId(), c.getCertificateNumber(), c.getIssuedAt(),
                        c.getEnrollment().getCourse().getTitle(), c.getEnrollment().getCourse().getSubject().getName()))
                .toList();
    }

    private ProfileResponse toResponse(User user, UserProfile p) {
        return new ProfileResponse(user.getEmail(), user.getRole().name(), p == null ? null : p.getFullName(),
                p == null ? null : p.getPhone(), p == null ? null : p.getDateOfBirth(),
                p == null ? null : p.getOrganization(), p == null ? null : p.getBio(),
                p == null || p.getAvatarObjectKey() == null ? null : storageService.presignView(p.getAvatarObjectKey()));
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static final java.util.Set<String> IMAGE_TYPES = java.util.Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    public record AvatarUpload(String uploadUrl, String objectKey) {}

    /** Step 1: validate and presign. The key is only attached to the profile after the client confirms the upload. */
    public AvatarUpload requestAvatarUpload(String fileName, String mimeType, long fileSize) {
        if (mimeType == null || !IMAGE_TYPES.contains(mimeType)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh đại diện phải là PNG, JPG, WEBP hoặc GIF");
        }
        if (fileSize <= 0 || fileSize > MAX_AVATAR_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh đại diện tối đa 2MB");
        }
        String key = storageService.newObjectKey(fileName).replaceFirst("^lesson-contents/", "avatars/");
        return new AvatarUpload(storageService.presignUpload(key, mimeType), key);
    }

    @Transactional
    public ProfileResponse confirmAvatar(UUID userId, String objectKey) {
        if (objectKey == null || !objectKey.startsWith("avatars/")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid avatar key");
        }
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserProfile profile = profiles.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Profile not found"));
        profile.setAvatarObjectKey(objectKey);
        return toResponse(user, profiles.save(profile));
    }

    @Transactional
    public ProfileResponse removeAvatar(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
        UserProfile profile = profiles.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Profile not found"));
        profile.setAvatarObjectKey(null);
        return toResponse(user, profiles.save(profile));
    }
}
