package com.oep.corelms.dto.admin;

public record AdminUserStatsResponse(
        long totalUsers,
        long activeUsers,
        long lockedUsers
) {}
