package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.VideoProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VideoProcessingJobRepository extends JpaRepository<VideoProcessingJob, UUID> {
    Optional<VideoProcessingJob> findTopByLessonContentIdOrderByCreatedAtDesc(UUID contentId);
}
