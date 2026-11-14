package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.ExamScoreSyncEvent;
import com.oep.worker.service.ExamScoreSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreSyncListener {

    private final ExamScoreSyncService scoreSync;

    @RabbitListener(queues = MessagingConstants.QUEUE_SCORE_SYNC)
    public void onScoreSync(ExamScoreSyncEvent event) {
        scoreSync.sync(event);
        log.info("Synced exam score for attempt {}", event.attemptId());
    }
}
