package com.oep.corelms.service;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.exception.BusinessException;
import com.oep.coremodel.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CoursePublishValidator {
    private final JdbcTemplate jdbc;

    public void validate(Course course) {
        if (!validWeights(course.getWeightPosttest(), course.getWeightMassExam(), course.getWeightAssignment())) {
            throw new BusinessException(ErrorCode.COURSE_NOT_READY, "Tổng trọng số phải bằng 100%");
        }
        List<Coverage> outcomes = jdbc.query("""
                with materials as (
                    select lc.lo_id, count(*) as total from lesson_contents lc
                    join lessons l on l.id = lc.lesson_id
                    join course_modules m on m.id = l.module_id
                    where m.course_id = ? and lc.upload_status = 'AVAILABLE'
                      and lc.content_type in ('VIDEO', 'DOCUMENT', 'PDF')
                    group by lc.lo_id
                ), assessments as (
                    select q.lo_id,
                           count(distinct q.id) filter (where z.assessment_kind = 'MINI_QUIZ') as mini,
                           count(distinct q.id) filter (where z.assessment_kind in ('POST_TEST', 'MASS_EXAM')) as official
                    from questions q join quiz_questions qq on qq.question_id = q.id
                    join quizzes z on z.id = qq.quiz_id
                    where z.course_id = ? and q.approval_state = 'APPROVED'
                    group by q.lo_id
                )
                select lo.code, coalesce(m.total, 0), coalesce(a.mini, 0), coalesce(a.official, 0)
                from learning_outcomes lo left join materials m on m.lo_id = lo.id
                left join assessments a on a.lo_id = lo.id
                where lo.course_id = ? order by lo.code
                """, (row, index) -> new Coverage(row.getString(1), row.getLong(2), row.getLong(3), row.getLong(4)),
                course.getId(), course.getId(), course.getId());
        if (outcomes.isEmpty()) {
            throw new BusinessException(ErrorCode.COURSE_NOT_READY, "Khóa học cần ít nhất một chuẩn đầu ra");
        }
        var incomplete = outcomes.stream()
                .filter(lo -> lo.materials() < 1 || lo.miniQuiz() < 3 || lo.official() < 1)
                .map(lo -> "%s (học liệu %d/1, Mini-quiz %d/3, câu thi %d/1)"
                        .formatted(lo.code(), lo.materials(), lo.miniQuiz(), lo.official()))
                .toList();
        if (!incomplete.isEmpty()) {
            throw new BusinessException(ErrorCode.COURSE_NOT_READY, String.join("; ", incomplete));
        }
    }

    public static boolean validWeights(Integer postTest, Integer massExam, Integer assignment) {
        return validWeight(postTest) && validWeight(massExam) && validWeight(assignment)
                && postTest + massExam + assignment == 100;
    }

    private static boolean validWeight(Integer weight) {
        return weight != null && weight >= 0 && weight <= 100;
    }

    private record Coverage(String code, long materials, long miniQuiz, long official) {}
}
