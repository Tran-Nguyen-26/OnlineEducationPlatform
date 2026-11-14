package com.oep.corelms.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LessonContentReferenceRepository {
    @PersistenceContext
    private EntityManager entityManager;

    public boolean isReferenced(UUID contentId) {
        return hasReference("select count(p) from LessonProgress p where p.lastContent.id = :contentId", contentId)
                || hasReference("select count(c) from AiDocumentChunk c where c.content.id = :contentId", contentId)
                || hasReference("select count(c) from ContentChangeLog c where c.content.id = :contentId", contentId);
    }

    public boolean isLessonReferenced(UUID lessonId) {
        return hasLessonReference("select count(p) from LessonProgress p where p.lesson.id = :lessonId", lessonId)
                || hasLessonReference("select count(c) from AiDocumentChunk c where c.lesson.id = :lessonId", lessonId)
                || hasLessonReference("select count(c) from AiConversation c where c.lesson.id = :lessonId", lessonId)
                || hasLessonReference("select count(a) from Assignment a where a.lesson.id = :lessonId", lessonId)
                || hasLessonReference("select count(q) from Quiz q where q.lesson.id = :lessonId", lessonId)
                || hasLessonReference("select count(r) from LessonCompletionRule r where r.lesson.id = :lessonId", lessonId)
                || hasQuestionReference(lessonId);
    }

    private boolean hasReference(String jpql, UUID contentId) {
        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("contentId", contentId)
                .getSingleResult();
        return count > 0;
    }

    private boolean hasLessonReference(String jpql, UUID lessonId) {
        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("lessonId", lessonId)
                .getSingleResult();
        return count > 0;
    }

    private boolean hasQuestionReference(UUID lessonId) {
        Number count = (Number) entityManager.createNativeQuery(
                "select count(*) from lesson_questions where lesson_id = :lessonId")
                .setParameter("lessonId", lessonId)
                .getSingleResult();
        return count.longValue() > 0;
    }
}
