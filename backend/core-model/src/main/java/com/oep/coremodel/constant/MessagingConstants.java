package com.oep.coremodel.constant;

public final class MessagingConstants {
    private MessagingConstants() {}

    public static final String EXCHANGE = "oep.events";

    public static final String QUEUE_NOTIFICATION = "oep.worker.notification";
    public static final String QUEUE_SCORE_SYNC = "oep.worker.score-sync";

    public static final String RK_QUIZ_PUBLISHED = "quiz.published";
    public static final String RK_NOTIFICATION = "notification.requested";
    public static final String RK_SCORE_SYNC = "exam.score.sync";
    public static final String RK_FORGOT_PASSWORD_EMAIL = "auth.forgot-password.email";
    public static final String QUEUE_FORGOT_PASSWORD_EMAIL = "oep.worker.forgot-password-email";

    public static final String RK_INSTRUCTOR_APPROVED = "instructor.application.approved";
    public static final String RK_INSTRUCTOR_REJECTED = "instructor.application.rejected";
    public static final String QUEUE_INSTRUCTOR_APPLICATION = "oep.worker.instructor-application";
}
