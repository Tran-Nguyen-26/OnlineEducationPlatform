package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    List<AttendanceRecord> findBySession_Id(UUID sessionId);

    List<AttendanceRecord> findBySession_Course_Id(UUID courseId);

    Optional<AttendanceRecord> findBySession_IdAndEnrollment_Id(UUID sessionId, UUID enrollmentId);

    void deleteBySession_Id(UUID sessionId);
}
