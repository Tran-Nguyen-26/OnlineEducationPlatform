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

    private boolean hasReference(String jpql, UUID contentId) {
        Long count = entityManager.createQuery(jpql, Long.class)
                .setParameter("contentId", contentId)
                .getSingleResult();
        return count > 0;
    }
}
