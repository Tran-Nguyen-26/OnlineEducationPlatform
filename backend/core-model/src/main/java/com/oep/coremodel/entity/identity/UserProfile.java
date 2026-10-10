package com.oep.coremodel.entity.identity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 1-1 with users, shares the primary key (user_id). */
@Getter
@Setter
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "avatar_object_key", length = 500)
    private String avatarObjectKey;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "bio", columnDefinition = "text")
    private String bio;

    @Column(name = "organization", length = 255)
    private String organization;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
