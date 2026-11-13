package com.oep.corelms.service;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CoursePublishValidatorTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CoursePublishValidator validator = new CoursePublishValidator(jdbc);

    @Test
    void rejectsUnsetOrIncorrectWeightsBeforeCheckingCoverage() {
        Course course = course();
        course.setWeightAssignment(10);
        assertThatThrownBy(() -> validator.validate(course))
                .isInstanceOf(BusinessException.class).hasMessageContaining("100%");
        verifyNoInteractions(jdbc);
    }

    @Test
    void requiresAtLeastOneOutcome() {
        Course course = course();
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<Object>>any(), any(), any(), any()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> validator.validate(course))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chuẩn đầu ra");
    }

    @Test
    void rejectsMissingMiniQuizCoverage() throws Exception {
        Course course = course();
        coverage(1, 2, 1);
        assertThatThrownBy(() -> validator.validate(course))
                .isInstanceOf(BusinessException.class).hasMessageContaining("LO-1", "Mini-quiz 2/3");
    }

    @Test
    void acceptsCourseWithCompleteCoverage() throws Exception {
        coverage(1, 3, 1);
        validator.validate(course());
    }

    private void coverage(long materials, long miniQuiz, long official) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString(1)).thenReturn("LO-1");
        when(row.getLong(2)).thenReturn(materials);
        when(row.getLong(3)).thenReturn(miniQuiz);
        when(row.getLong(4)).thenReturn(official);
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<Object>>any(), any(), any(), any()))
                .thenAnswer(call -> {
                    RowMapper<Object> mapper = call.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });
    }

    private Course course() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setWeightPosttest(30);
        course.setWeightMassExam(50);
        course.setWeightAssignment(20);
        return course;
    }
}
