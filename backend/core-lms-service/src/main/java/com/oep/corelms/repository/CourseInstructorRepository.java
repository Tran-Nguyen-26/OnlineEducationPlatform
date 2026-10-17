package com.oep.corelms.repository;
import com.oep.coremodel.entity.course.CourseInstructor;
import com.oep.coremodel.entity.course.CourseInstructorId;
import com.oep.coremodel.enums.CourseInstructorRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, CourseInstructorId> {
    boolean existsByCourseIdAndInstructorIdAndCourseRole(UUID courseId, UUID instructorId, CourseInstructorRole role);
}