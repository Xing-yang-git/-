package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrow_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idle_id", nullable = false)
    private Long idleId;

    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;

    @Column(name = "duration_type", nullable = false, length = 10)
    private String durationType;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(length = 200)
    private String note;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "handoff_photos", columnDefinition = "TEXT")
    private String handoffPhotos;

    @Column(name = "return_status", length = 20)
    private String returnStatus;

    @Column(name = "return_note", length = 200)
    private String returnNote;

    @Column(name = "damage_type", length = 20)
    private String damageType;

    @Column(name = "damage_note", length = 200)
    private String damageNote;

    @Column(name = "is_on_time")
    private Boolean isOnTime;

    @Column(name = "return_photos", columnDefinition = "TEXT")
    private String returnPhotos;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idle_id", insertable = false, updatable = false)
    private IdleItem idleItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrower_id", insertable = false, updatable = false)
    private User borrower;

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
