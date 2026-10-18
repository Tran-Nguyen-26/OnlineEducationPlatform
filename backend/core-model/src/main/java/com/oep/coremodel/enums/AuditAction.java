package com.oep.coremodel.enums;

public enum AuditAction {
    // Module 1: Identity & IAM
    DISABLE_USER,
    ENABLE_USER,
    CHANGE_USER_ROLE,
    REVOKE_ALL_SESSIONS,
    ADMIN_RESET_PASSWORD,

    // Module 2: Course & Media
    DISABLE_COURSE,
    APPROVE_COURSE,
    REJECT_COURSE,
    TRANSFER_COURSE_OWNERSHIP,
    ASSIGN_CO_INSTRUCTOR,
    REMOVE_CO_INSTRUCTOR,

    // Module 3: Enrollment & Progress
    FORCE_REMOVE_ENROLLMENT,
    ADMIN_MANUAL_ENROLL,
    RESTORE_ENROLLMENT,

    // Module 4: Assessment - Quiz Engine
    RESET_QUIZ_ATTEMPT,
    ADJUST_QUIZ_SCORE,
    UNLOCK_QUIZ_CONTENT,

    // Module 5: Assessment - Assignment Engine
    REGRADE,
    ADJUST_SCORE,
    EXTEND_ASSIGNMENT_DEADLINE,

    // Module 6: Notifications & System Reliability
    UPDATE_SYSTEM_SETTING,
    BROADCAST_SYSTEM_ALERT,
    RETRY_FAILED_OUTBOX,

    // Module 8: Reviews & Communication
    MODERATE_HIDE_REVIEW,
    UNHIDE_REVIEW,
    MUTE_CHANNEL_MEMBER,

    // Module 9: Applications & Invitations
    APPROVE_INSTRUCTOR_APPLICATION,
    REJECT_INSTRUCTOR_APPLICATION,
    REVOKE_INVITE_CODE,

    // Module 10: AI Tutor Knowledge Assistant
    TOGGLE_COURSE_AI_TUTOR,
    PURGE_AI_KNOWLEDGE_BASE
}
