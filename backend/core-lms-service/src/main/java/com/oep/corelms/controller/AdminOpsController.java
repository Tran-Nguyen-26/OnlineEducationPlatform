package com.oep.corelms.controller;

import com.oep.corelms.repository.OutboxEventRepository;
import com.oep.corelms.service.AuditLogService;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.enums.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Operations view of the async pipeline: outbox backlog, notification queue depth and the dead-letter queue (with replay). */
@RestController
@RequestMapping("/api/admin/ops")
@RequiredArgsConstructor
public class AdminOpsController {

    private final OutboxEventRepository outbox;
    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbit;
    private final AuditLogService auditLogService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        Instant oldest = outbox.oldestPending();
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("pending", outbox.countByStatus(OutboxStatus.PENDING));
        o.put("processed", outbox.countByStatus(OutboxStatus.PROCESSED));
        o.put("failed", outbox.countByStatus(OutboxStatus.FAILED));
        o.put("oldestPendingAgeSeconds", oldest == null ? 0 : Duration.between(oldest, Instant.now()).getSeconds());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("outbox", o);
        out.put("notificationQueue", queue(MessagingConstants.QUEUE_NOTIFICATION));
        out.put("deadLetterQueue", queue(MessagingConstants.QUEUE_NOTIFICATION_DLQ));
        return out;
    }

    @PostMapping("/outbox/retry-failed")
    @Transactional
    public Map<String, Object> retryFailed(@AuthenticationPrincipal Jwt jwt) {
        int n = outbox.resetFailed();
        auditLogService.record(UUID.fromString(jwt.getSubject()), "OPS_RETRY_FAILED_OUTBOX", "OUTBOX", UUID.randomUUID(), Map.of("rows", String.valueOf(n)));
        return Map.of("requeued", n);
    }

    /** Moves up to {@code max} dead-lettered notifications back to the exchange for another delivery attempt. */
    @PostMapping("/dlq/replay")
    public Map<String, Object> replay(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "100") int max) {
        int replayed = 0;
        for (int i = 0; i < Math.min(max, 1000); i++) {
            Message m = rabbit.receive(MessagingConstants.QUEUE_NOTIFICATION_DLQ, 200);
            if (m == null) break;
            m.getMessageProperties().getHeaders().remove("x-death");
            rabbit.send(MessagingConstants.EXCHANGE, MessagingConstants.RK_NOTIFICATION, m);
            replayed++;
        }
        auditLogService.record(UUID.fromString(jwt.getSubject()), "OPS_REPLAY_DLQ", "QUEUE", UUID.randomUUID(), Map.of("messages", String.valueOf(replayed)));
        return Map.of("replayed", replayed);
    }

    private Map<String, Object> queue(String name) {
        QueueInformation info = amqpAdmin.getQueueInfo(name);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("messages", info == null ? -1 : info.getMessageCount());
        m.put("consumers", info == null ? 0 : info.getConsumerCount());
        return m;
    }
}
