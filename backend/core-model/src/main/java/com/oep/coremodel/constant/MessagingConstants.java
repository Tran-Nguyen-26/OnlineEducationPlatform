package com.oep.coremodel.constant;

public final class MessagingConstants {
    private MessagingConstants() {}

    public static final String EXCHANGE = "oep.events";

    /** v2: declared with a dead-letter exchange (a queue's arguments cannot be changed in place, hence the new name). */
    public static final String QUEUE_NOTIFICATION = "oep.worker.notification.v2";
    public static final String DLX = "oep.events.dlx";
    public static final String QUEUE_NOTIFICATION_DLQ = "oep.worker.notification.dlq";
    public static final String DLQ_ROUTING_KEY = "notification.dead";
    public static final String QUEUE_VIDEO = "oep.worker.video";
    public static final String QUEUE_VIDEO_DLQ = "oep.worker.video.dlq";
    public static final String DLQ_VIDEO_KEY = "video.dead";
    public static final String QUEUE_SCORE_SYNC = "oep.worker.score-sync";

    public static final String RK_QUIZ_PUBLISHED = "quiz.published";
    public static final String RK_NOTIFICATION = "notification.requested";
    public static final String RK_VIDEO_TRANSCODE = "video.transcode";
    public static final String RK_AI_INDEX = "ai.index.requested";
    public static final String RK_SCORE_SYNC = "exam.score.sync";
}
