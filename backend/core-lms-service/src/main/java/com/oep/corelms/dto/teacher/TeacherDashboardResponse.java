package com.oep.corelms.dto.teacher;

import java.util.List;
import java.util.UUID;

public record TeacherDashboardResponse(int courseCount, int activeStudents, List<CourseStat> courses) {

    public record CourseStat(UUID courseId, String title, String subjectName, String lifecycleStatus, long activeStudents,
                             long completedStudents, long waitlistedStudents, Integer capacity) {}
}
