package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.ExamScoreSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScoreSyncListener {

    @RabbitListener(queues = MessagingConstants.QUEUE_SCORE_SYNC)
    public void onScoreSync(ExamScoreSyncEvent event) {
        // TODO: write score/status of the attempt from Redis into quiz_attempts (idempotent upsert)
        log.info("Sync score for attempt {}", event.attemptId());
    }
}
