package com.oep.worker.repository;

import com.oep.coremodel.entity.course.VideoProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VideoProcessingJobRepository extends JpaRepository<VideoProcessingJob, UUID> {
}
