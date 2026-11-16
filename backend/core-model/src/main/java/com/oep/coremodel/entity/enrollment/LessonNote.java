package com.oep.coremodel.entity.enrollment;

import com.oep.coremodel.entity.BaseEntity;
import com.oep.coremodel.entity.course.Lesson;
import com.oep.coremodel.entity.identity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** A student's private notes for one lesson (one row per student+lesson, autosaved by the learning page). */
@Getter
@Setter
@Entity
@Table(name = "lesson_notes", uniqueConstraints = {@UniqueConstraint(name = "uq_lesson_notes_student_lesson", columnNames = {"student_id", "lesson_id"})})
public class LessonNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content = "";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
