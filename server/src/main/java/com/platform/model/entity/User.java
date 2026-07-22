package com.platform.model.entity;

import com.platform.common.BizStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"phone", "tenant_id"}),
    @UniqueConstraint(columnNames = {"room_id", "user_type"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(length = 64, unique = true)
    private String openid;

    @Column(length = 50, unique = true)
    private String username;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "user_type", nullable = false, length = 20)
    @Builder.Default
    private String userType = "业主";

    @Column(length = 50)
    private String name;

    @Column(length = 11)
    private String phone;

    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "auth_status", nullable = false, length = 20)
    @Builder.Default
    private String authStatus = BizStatus.PENDING;

    @Column(name = "banned_reason", length = 200)
    private String bannedReason;

    @Column(name = "doc_images", columnDefinition = "TEXT")
    private String docImages;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    @Column(name = "token_version", nullable = false)
    @Builder.Default
    private Integer tokenVersion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", insertable = false, updatable = false)
    private Room room;

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
