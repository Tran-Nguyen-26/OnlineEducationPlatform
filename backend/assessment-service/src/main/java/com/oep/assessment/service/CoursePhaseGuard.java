package com.oep.assessment.service;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;

/** Learners can only take quizzes / submit work while the course is running (between its start and end dates, when set). */
final class CoursePhaseGuard {

    private CoursePhaseGuard() {}

    static void requireOpen(Course course) {
        switch (course.currentPhase()) {
            case UPCOMING -> throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Khóa học chưa bắt đầu");
            case ENDED -> throw new BusinessException(ErrorCode.COURSE_NOT_OPEN, "Khóa học đã kết thúc");
            default -> { }
        }
    }
}
