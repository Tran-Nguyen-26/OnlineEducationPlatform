package com.oep.coremodel.enums;

/** Adaptive-streaming (HLS) preparation of an uploaded video. Until READY the original file is played as a fallback. */
public enum TranscodeStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}
