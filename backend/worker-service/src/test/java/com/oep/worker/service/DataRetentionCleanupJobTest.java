package com.oep.worker.service;

import com.oep.worker.repository.NotificationRepository;
import com.oep.worker.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataRetentionCleanupJobTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private DataRetentionCleanupJob cleanupJob;

    @Test
    @DisplayName("Should invoke purge queries with appropriate cutoffs for outbox and notifications")
    void purgeOldRecords_Success() {
        when(outboxEventRepository.deleteProcessedEventsOlderThan(any(Instant.class))).thenReturn(42);
        when(notificationRepository.deleteReadNotificationsOlderThan(any(Instant.class))).thenReturn(15);

        cleanupJob.purgeOldRecords();

        verify(outboxEventRepository).deleteProcessedEventsOlderThan(any(Instant.class));
        verify(notificationRepository).deleteReadNotificationsOlderThan(any(Instant.class));
    }
}
