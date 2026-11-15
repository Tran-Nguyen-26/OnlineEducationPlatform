package com.oep.corelms.repository;

import com.oep.coremodel.entity.social.CourseChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseChannelRepository extends JpaRepository<CourseChannel, UUID> {
    Optional<CourseChannel> findByCourse_Id(UUID courseId);
}
