package com.oep.corelms.dto.instructor;

public record InstructorApplicationStatsResponse(
        long total,
        long pending,
        long approved,
        long rejected
) {}
