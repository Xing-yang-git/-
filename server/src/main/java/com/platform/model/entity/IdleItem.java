package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idle_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "post_type", nullable = false, length = 10)
    @Builder.Default
    private String postType = "LEND";

    @Column(nullable = false, length = 30)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String condition = "normal";

    @Column(nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String images;

    @Column(name = "max_duration")
    @Builder.Default
    private Integer maxDuration = 7;

    @Column(name = "duration_unit", nullable = false, length = 10)
    @Builder.Default
    private String durationUnit = "day";

    @Column(name = "pickup_method", nullable = false, length = 30)
    @Builder.Default
    private String pickupMethod = "self_pickup";

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "violated_by", insertable = false, updatable = false)
    private User violator;

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
