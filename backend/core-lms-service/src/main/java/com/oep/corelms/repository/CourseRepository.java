package com.oep.corelms.repository;

import com.oep.coremodel.entity.course.Course;
import com.oep.coremodel.enums.CourseLifecycleStatus;
import com.oep.coremodel.enums.CourseVisibility;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c from Course c where c.visibility = :visibility and c.lifecycleStatus = :status and lower(c.title) like lower(concat('%', :search, '%'))")
    Page<Course> findCatalog(@Param("visibility") CourseVisibility visibility,
                             @Param("status") CourseLifecycleStatus status,
                             @Param("search") String search, Pageable pageable);

    @Query("select c from Course c where exists (select ci from CourseInstructor ci where ci.course = c and ci.instructor.id = :instructorId)")
    Page<Course> findManagedByInstructor(@Param("instructorId") UUID instructorId, Pageable pageable);

    @Query("""
            select c from Course c
            where lower(c.title) like lower(concat('%', :search, '%'))
              and (:admin = true
                or exists (select ci from CourseInstructor ci where ci.course = c and ci.instructor.id = :actor)
                or (c.lifecycleStatus in ('PUBLISHED', 'ARCHIVED')
                  and exists (select e from Enrollment e where e.course = c and e.student.id = :actor
                    and e.status in ('ACTIVE', 'COMPLETED'))))
            """)
    Page<Course> findVisibleTo(@Param("actor") UUID actor, @Param("admin") boolean admin,
                               @Param("search") String search, Pageable pageable);

    @Query("""
            select count(e) > 0 from Enrollment e
            where e.course.id = :course and e.student.id = :actor and e.status in ('ACTIVE', 'COMPLETED')
            """)
    boolean hasReadableEnrollment(@Param("course") UUID course, @Param("actor") UUID actor);
}
