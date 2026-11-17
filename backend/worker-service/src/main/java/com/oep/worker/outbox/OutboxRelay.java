package com.oep.worker.outbox;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.coremodel.enums.OutboxStatus;
import com.oep.worker.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Publishes pending outbox rows to RabbitMQ. Each row is sent on a confirm-enabled channel and only marked PROCESSED
 * after the broker acknowledged it; otherwise it is retried with exponential backoff, and parked as FAILED after
 * {@value #MAX_ATTEMPTS} attempts (visible and retryable from the admin "Vận hành" page).
 * Delivery is at-least-once; the notification consumer is idempotent (dedupeKey), so duplicates are harmless.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    static final int MAX_ATTEMPTS = 10;
    private static final int BATCH = 100;

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final io.micrometer.core.instrument.MeterRegistry meters;

    @jakarta.annotation.PostConstruct
    void registerGauges() {
        meters.gauge("oep.outbox.pending", outbox, r -> r.countByStatus(OutboxStatus.PENDING));
        meters.gauge("oep.outbox.failed", outbox, r -> r.countByStatus(OutboxStatus.FAILED));
    }

    @Scheduled(fixedDelayString = "${oep.outbox.poll-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> due = outbox.lockDueBatch(BATCH);
        if (due.isEmpty()) return;
        int sent = 0;
        for (OutboxEvent e : due) {
            try {
                Message m = MessageBuilder.withBody(e.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(e.getId().toString())
                        .build();
                Boolean confirmed = rabbit.invoke(ops -> {
                    ops.send(MessagingConstants.EXCHANGE, e.getEventType(), m);
                    return ops.waitForConfirms(5000);
                });
                if (!Boolean.TRUE.equals(confirmed)) throw new IllegalStateException("broker did not confirm the message");
                meters.counter("oep.outbox.published").increment();
                e.setStatus(OutboxStatus.PROCESSED);
                e.setProcessedAt(Instant.now());
                e.setLastError(null);
                sent++;
            } catch (Exception ex) {
                int attempts = e.getRetryCount() + 1;
                e.setRetryCount(attempts);
                e.setLastError(String.valueOf(ex.getMessage()));
                if (attempts >= MAX_ATTEMPTS) {
                    e.setStatus(OutboxStatus.FAILED);
                    log.error("Outbox event {} parked as FAILED after {} attempts: {}", e.getId(), attempts, ex.getMessage());
                } else {
                    e.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(Math.min(300, 1L << attempts))));
                }
                // the broker is unavailable: stop the batch, later rows would fail the same way
                if (ex instanceof org.springframework.amqp.AmqpException) break;
            }
        }
        outbox.saveAll(due);
        if (sent > 0) log.debug("Outbox relay published {} event(s)", sent);
    }
}
