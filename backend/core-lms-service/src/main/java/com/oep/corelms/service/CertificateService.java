package com.oep.corelms.service;

import com.oep.corelms.dto.student.CertificateResponse;
import com.oep.corelms.repository.CertificateRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.enrollment.Certificate;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

/** Certificates (GAPS.md "Nhóm A"): auto-issued once per enrollment the moment Admin marks it COMPLETED. */
@Service
@RequiredArgsConstructor
public class CertificateService {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CertificateRepository certificates;
    private final EnrollmentRepository enrollments;
    private final UserProfileRepository userProfiles;
    private final EventNotifier notifier;

    /** No-op if a certificate for this enrollment already exists (e.g. Admin flips status back and forth). */
    @Transactional
    public void issueIfAbsent(Enrollment enrollment) {
        if (certificates.findByEnrollment_Id(enrollment.getId()).isPresent()) {
            return;
        }
        Certificate certificate = new Certificate();
        certificate.setEnrollment(enrollment);
        certificate.setCertificateNumber(generateUniqueNumber());
        certificates.save(certificate);
        notifier.send(enrollment.getStudent().getId(), "CERTIFICATE_ISSUED", "Bạn đã nhận chứng chỉ",
                "Chúc mừng! Chứng chỉ khóa \"" + enrollment.getCourse().getTitle() + "\" (" + certificate.getCertificateNumber() + ") đã sẵn sàng.",
                "certificate:" + enrollment.getId(), enrollment.getCourse().getId());
    }

    @Transactional(readOnly = true)
    public CertificateResponse getForStudent(UUID enrollmentId, UUID studentId) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Enrollment not found"));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Not your enrollment");
        }
        Certificate certificate = certificates.findByEnrollment_Id(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "No certificate issued for this enrollment yet"));
        return toResponse(certificate);
    }

    @Transactional(readOnly = true)
    public CertificateResponse verify(String certificateNumber) {
        Certificate certificate = certificates.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Certificate not found"));
        return toResponse(certificate);
    }

    private CertificateResponse toResponse(Certificate certificate) {
        Enrollment enrollment = certificate.getEnrollment();
        String fullName = userProfiles.findById(enrollment.getStudent().getId())
                .map(UserProfile::getFullName)
                .orElse(null);
        return new CertificateResponse(certificate.getId(), certificate.getCertificateNumber(), certificate.getIssuedAt(),
                fullName, enrollment.getStudent().getEmail(), enrollment.getCourse().getTitle(),
                enrollment.getCourse().getSubject().getName());
    }

    private String generateUniqueNumber() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder("CERT-");
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!certificates.existsByCertificateNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique certificate number after 10 attempts");
    }
}
