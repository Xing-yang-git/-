package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "help_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "is_urgent", nullable = false)
    @Builder.Default
    private Boolean isUrgent = false;

    @Column(name = "time_start")
    private LocalDateTime timeStart;

    @Column(name = "time_end")
    private LocalDateTime timeEnd;

    @Column(length = 200)
    private String location;

    @Column(name = "reward_type", nullable = false, length = 20)
    @Builder.Default
    private String rewardType = "free";

    @Column(columnDefinition = "TEXT")
    private String images;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "online";

    @Column(name = "delist_reason", length = 200)
    private String delistReason;

    @Column(name = "is_proxy", nullable = false)
    @Builder.Default
    private Boolean isProxy = false;

    @Column(name = "violation_type", length = 20)
    private String violationType;

    @Column(name = "violation_reason", columnDefinition = "TEXT")
    private String violationReason;

    @Column(name = "violated_by")
    private UUID violatedBy;

    @Column(name = "violated_at")
    private LocalDateTime violatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
