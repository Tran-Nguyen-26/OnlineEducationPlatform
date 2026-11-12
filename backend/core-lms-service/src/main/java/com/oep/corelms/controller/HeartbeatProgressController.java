package com.oep.corelms.controller;

import com.oep.corelms.service.HeartbeatBuffer;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class HeartbeatProgressController {
    private final HeartbeatBuffer buffer;

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OpenRequest(@NotNull UUID enrollmentId, @NotNull UUID lessonId, @NotNull UUID videoId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Stream(UUID streamToken) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Heartbeat(@NotNull UUID enrollmentId, @NotNull UUID lessonId, @NotNull UUID streamToken,
                            @NotNull @Positive Integer sequence,
                            @NotNull @PositiveOrZero Integer currentPlaybackSec,
                            @NotNull @PositiveOrZero Integer watchedDeltaSec) {}

    @PostMapping("/streams")
    @ResponseStatus(HttpStatus.CREATED)
    public Stream open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenRequest request) {
        return new Stream(buffer.open(actor(jwt), request.enrollmentId(), request.lessonId(), request.videoId()));
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Heartbeat request) {
        buffer.heartbeat(actor(jwt), request.enrollmentId(), request.lessonId(), request.streamToken(),
                request.sequence(), request.currentPlaybackSec(), request.watchedDeltaSec());
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public java.util.Map<String, String> unavailable() {
        return java.util.Map.of("error", ErrorCode.HEARTBEAT_UNAVAILABLE.name(), "message", "Progress buffer unavailable");
    }

    private UUID actor(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Token subject must be a user UUID");
        }
    }
}
