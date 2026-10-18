package com.oep.worker.model;

public record VideoMetadata(
        int width,
        int height,
        double durationSeconds,
        double fps,
        String videoCodec,
        String audioCodec
) {}
