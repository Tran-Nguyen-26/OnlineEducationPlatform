package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModule_IdOrderByPositionAsc(UUID moduleId);

    long countByModule_Id(UUID moduleId);

    /** One grouped query instead of (modules x courses) counts. Rows: [courseId, lessonCount]. */
    @org.springframework.data.jpa.repository.Query("select m.course.id, count(l) from Lesson l join l.module m where m.course.id in :courseIds group by m.course.id")
    java.util.List<Object[]> countByCourseIds(java.util.Collection<UUID> courseIds);
}
