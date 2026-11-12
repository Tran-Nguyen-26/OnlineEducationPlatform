package com.oep.corelms.service;

import com.oep.corelms.repository.HeartbeatProgressRepository;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeartbeatBuffer {
    private static final String PREFIX = "oep:{m05}:";
    private static final String PENDING = PREFIX + "pending";
    private static final String INFLIGHT = PREFIX + "inflight";
    private static final String BATCH = PREFIX + "batch";
    private static final String URGENT = PREFIX + "urgent";
    private static final DefaultRedisScript<Long> OPEN = script("open", Long.class);
    private static final DefaultRedisScript<Long> ACCEPT = script("accept", Long.class);
    private static final DefaultRedisScript<List> SNAPSHOT = script("snapshot", List.class);
    private static final DefaultRedisScript<Long> ACK = script("ack", Long.class);

    private final StringRedisTemplate redis;
    private final HeartbeatProgressRepository repository;

    public UUID open(UUID actor, UUID enrollment, UUID lesson, UUID video) {
        int duration = repository.videoDuration(actor, enrollment, lesson, video)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Playable video enrollment required"));
        UUID token = UUID.randomUUID();
        redis.execute(OPEN, List.of(stream(actor)), token.toString(), enrollment.toString(), lesson.toString(),
                video.toString(), Integer.toString(duration));
        return token;
    }

    public void heartbeat(UUID actor, UUID enrollment, UUID lesson, UUID token, int sequence, int position, int delta) {
        if (!repository.canWrite(actor, enrollment)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Active enrollment required");
        }
        Long outcome = redis.execute(ACCEPT, List.of(stream(actor), PENDING, URGENT,
                        PREFIX + "watched:" + enrollment), token.toString(), enrollment.toString(), lesson.toString(),
                Integer.toString(sequence), Integer.toString(position), Integer.toString(delta));
        if (Long.valueOf(-1).equals(outcome)) {
            throw new BusinessException(ErrorCode.STREAM_SUPERSEDED, "Playback stream expired or was replaced");
        }
        if (outcome == null) {
            throw new BusinessException(ErrorCode.HEARTBEAT_UNAVAILABLE, "Progress buffer did not acknowledge the heartbeat");
        }
    }

    @Scheduled(fixedDelayString = "${oep.progress.flush-ms:120000}")
    public void flush() {
        List<?> batch = redis.execute(SNAPSHOT, List.of(PENDING, INFLIGHT, BATCH, URGENT),
                UUID.randomUUID().toString());
        if (batch == null || batch.isEmpty()) {
            return;
        }
        String batchId = (String) batch.getFirst();
        String rows = batch.subList(1, batch.size()).stream().map(String.class::cast)
                .collect(Collectors.joining(",", "[", "]"));

        // JdbcTemplate executes this single statement in autocommit. ACK only after it returns.
        repository.persist(UUID.fromString(batchId), rows);
        redis.execute(ACK, List.of(INFLIGHT, BATCH), batchId);
    }

    @Scheduled(fixedDelay = 1000)
    void flushMilestone() {
        if (Boolean.TRUE.equals(redis.hasKey(URGENT))) {
            flush();
        }
    }

    private String stream(UUID actor) {
        return PREFIX + "stream:lock:" + actor;
    }

    private static <T> DefaultRedisScript<T> script(String name, Class<T> type) {
        var script = new DefaultRedisScript<T>();
        script.setLocation(new ClassPathResource("redis/heartbeat/" + name + ".lua"));
        script.setResultType(type);
        return script;
    }
}
