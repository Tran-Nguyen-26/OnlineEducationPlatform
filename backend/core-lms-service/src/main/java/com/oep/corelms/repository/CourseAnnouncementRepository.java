package com.oep.corelms.repository;

import com.oep.coremodel.entity.social.CourseAnnouncement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement, UUID> {
    List<CourseAnnouncement> findByCourse_IdOrderByCreatedAtDesc(UUID courseId);
}
