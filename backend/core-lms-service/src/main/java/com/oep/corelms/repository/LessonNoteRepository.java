package com.oep.corelms.repository;

import com.oep.coremodel.entity.enrollment.LessonNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LessonNoteRepository extends JpaRepository<LessonNote, UUID> {
    Optional<LessonNote> findByStudent_IdAndLesson_Id(UUID studentId, UUID lessonId);
}
