package com.oep.worker.service;

import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.course.VideoProcessingJob;
import com.oep.coremodel.enums.UploadStatus;
import com.oep.coremodel.enums.VideoJobStatus;
import com.oep.worker.repository.LessonContentRepository;
import com.oep.worker.repository.VideoProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobStatusService {

    private final VideoProcessingJobRepository jobRepository;
    private final LessonContentRepository lessonContentRepository;

    @Transactional
    public void startJob(UUID jobId) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(VideoJobStatus.DOWNLOADING);
            job.setProgressPct(5);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);
            log.info("Job {} đã bắt đầu: DOWNLOADING (5%)", jobId);
        });
    }

    @Transactional
    public void updateProgress(UUID jobId, VideoJobStatus status, int progressPct, List<String> renditionsDone) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setProgressPct(progressPct);
            if (renditionsDone != null && !renditionsDone.isEmpty()) {
                job.setRenditionsDone(renditionsDone);
            }
            jobRepository.save(job);
            log.info("Cập nhật Job {}: status={}, progress={}%", jobId, status, progressPct);
        });
    }

    @Transactional
    public void markDone(UUID jobId, UUID contentId, String hlsMasterKey, String thumbKey, String spriteKey, int durationSeconds) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(VideoJobStatus.DONE);
            job.setProgressPct(100);
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        });

        lessonContentRepository.findById(contentId).ifPresent(content -> {
            content.setUploadStatus(UploadStatus.AVAILABLE);
            content.setHlsMasterKey(hlsMasterKey);
            content.setThumbnailKey(thumbKey);
            content.setSpriteSheetKey(spriteKey);
            content.setDurationSeconds(durationSeconds);
            lessonContentRepository.save(content);
            log.info("LessonContent {} đã AVAILABLE với HLS master: {}", contentId, hlsMasterKey);
        });
    }

    @Transactional
    public void markFailed(UUID jobId, UUID contentId, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(VideoJobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        });

        lessonContentRepository.findById(contentId).ifPresent(content -> {
            content.setUploadStatus(UploadStatus.FAILED);
            content.setProcessingError(errorMessage);
            lessonContentRepository.save(content);
            log.warn("LessonContent {} đánh dấu FAILED: {}", contentId, errorMessage);
        });
    }
}
