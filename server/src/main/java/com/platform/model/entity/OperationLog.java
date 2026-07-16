package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "operation_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", insertable = false, updatable = false)
    private User admin;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
