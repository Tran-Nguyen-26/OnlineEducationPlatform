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
                p == null ? null : p.getOrganization(), p == null ? null : p.getBio());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
