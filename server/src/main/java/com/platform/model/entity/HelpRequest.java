package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "help_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 200)
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

    @Column(name = "violation_reason", length = 200)
    private String violationReason;

    @Column(name = "violated_by")
    private Long violatedBy;

    @Column(name = "violated_at")
    private LocalDateTime violatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

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
