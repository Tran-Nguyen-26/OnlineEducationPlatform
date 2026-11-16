package com.oep.corelms.service;

import com.oep.corelms.dto.PaymentStatus;
import com.oep.corelms.dto.admin.FinanceDtos;
import com.oep.corelms.repository.CourseRepository;
import com.oep.corelms.repository.EnrollmentRepository;
import com.oep.corelms.repository.TuitionPaymentRepository;
import com.oep.corelms.repository.UserProfileRepository;
import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.entity.enrollment.Enrollment;
import com.oep.coremodel.entity.enrollment.TuitionPayment;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.identity.UserProfile;
import com.oep.coremodel.enums.EnrollmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Center-wide tuition reporting: only ACTIVE/COMPLETED enrollments owe tuition (withdrawn/removed/waitlisted rows don't). */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private static final Set<EnrollmentStatus> BILLABLE = EnumSet.of(EnrollmentStatus.ACTIVE, EnrollmentStatus.COMPLETED);

    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final TuitionPaymentRepository payments;
    private final UserProfileRepository userProfiles;
    private final TuitionPaymentService tuitionPaymentService;

    @Transactional(readOnly = true)
    public List<FinanceDtos.CourseRow> byCourse() {
        Map<UUID, BigDecimal> paidByEnrollment = paidByEnrollment();
        return courses.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getFeeAmount() != null && c.getFeeAmount().signum() > 0)
                .map(c -> courseRow(c, paidByEnrollment))
                .toList();
    }

    @Transactional(readOnly = true)
    public FinanceDtos.Summary summary() {
        List<FinanceDtos.CourseRow> rows = byCourse();
        BigDecimal expected = rows.stream().map(FinanceDtos.CourseRow::expected).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = rows.stream().map(FinanceDtos.CourseRow::collected).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FinanceDtos.Summary(expected, collected, expected.subtract(collected).max(BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.OutstandingRow> outstanding() {
        Map<UUID, BigDecimal> paidByEnrollment = paidByEnrollment();
        return enrollments.findAll().stream()
                .filter(e -> BILLABLE.contains(e.getStatus()))
                .filter(e -> e.getCourse().getFeeAmount() != null && e.getCourse().getFeeAmount().signum() > 0)
                .map(e -> toOutstanding(e, paidByEnrollment.getOrDefault(e.getId(), BigDecimal.ZERO)))
                .filter(r -> r.due().signum() > 0)
                .sorted(Comparator.comparing(FinanceDtos.OutstandingRow::due).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FinanceDtos.PaymentRow> recentPayments() {
        return payments.findTop200ByOrderByPaidAtDesc().stream().map(this::toPaymentRow).toList();
    }

    private FinanceDtos.CourseRow courseRow(Course c, Map<UUID, BigDecimal> paidByEnrollment) {
        List<Enrollment> billable = enrollments.findByCourseIdOrderByEnrolledAtDesc(c.getId()).stream()
                .filter(e -> BILLABLE.contains(e.getStatus())).toList();
        BigDecimal expected = c.getFeeAmount().multiply(BigDecimal.valueOf(billable.size()));
        BigDecimal collected = billable.stream().map(e -> paidByEnrollment.getOrDefault(e.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long fullyPaid = billable.stream()
                .filter(e -> paidByEnrollment.getOrDefault(e.getId(), BigDecimal.ZERO).compareTo(c.getFeeAmount()) >= 0).count();
        return new FinanceDtos.CourseRow(c.getId(), c.getTitle(), c.getFeeAmount(), fullyPaid, billable.size(), expected, collected,
                expected.subtract(collected).max(BigDecimal.ZERO));
    }

    private FinanceDtos.OutstandingRow toOutstanding(Enrollment e, BigDecimal paid) {
        User s = e.getStudent();
        BigDecimal fee = e.getCourse().getFeeAmount();
        return new FinanceDtos.OutstandingRow(e.getId(), s.getId(), name(s), s.getEmail(), e.getCourse().getId(),
                e.getCourse().getTitle(), e.getStatus().name(), fee, paid, fee.subtract(paid).max(BigDecimal.ZERO),
                tuitionPaymentService.status(fee, paid));
    }

    private FinanceDtos.PaymentRow toPaymentRow(TuitionPayment p) {
        Enrollment e = p.getEnrollment();
        return new FinanceDtos.PaymentRow(p.getId(), e.getId(), name(e.getStudent()), e.getStudent().getEmail(),
                e.getCourse().getTitle(), p.getAmount(), p.getNote(), p.getPaidAt(), p.getRecordedBy().getEmail());
    }

    private String name(User u) {
        return userProfiles.findById(u.getId()).map(UserProfile::getFullName).orElse(u.getEmail());
    }

    private Map<UUID, BigDecimal> paidByEnrollment() {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : payments.sumPaidByEnrollment()) {
            map.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return map;
    }
}
