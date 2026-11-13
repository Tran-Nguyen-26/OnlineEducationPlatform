package com.oep.coremodel.entity.course;

import com.oep.coremodel.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "learning_outcomes", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "code"}))
public class LearningOutcome extends BaseEntity {
    public enum Bloom { REMEMBER, UNDERSTAND, APPLY }

    @Column(name = "course_id", nullable = false)
    private UUID courseId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "bloom_level", nullable = false, length = 16)
    private Bloom bloom;
}
