package com.platform.model.entity;

import com.platform.common.BizStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "help_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "help_id", nullable = false)
    private Long helpId;

    @Column(name = "helper_id", nullable = false)
    private Long helperId;

    @Column(length = 200)
    private String note;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = BizStatus.PENDING;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "help_id", insertable = false, updatable = false)
    private HelpRequest helpRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "helper_id", insertable = false, updatable = false)
    private User helper;

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
