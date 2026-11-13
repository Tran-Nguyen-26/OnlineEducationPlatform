package com.oep.coremodel.entity.enrollment;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "archived_video_usage")
@IdClass(ArchivedVideoUsage.Key.class)
public class ArchivedVideoUsage {
    public record Key(UUID userId, LocalDate usageMonth) implements Serializable {}

    @Id
    @Column(name = "user_id")
    private UUID userId;
    @Id
    @Column(name = "usage_month")
    private LocalDate usageMonth;
    @Column(name = "seconds_used", nullable = false)
    private int secondsUsed;
}
