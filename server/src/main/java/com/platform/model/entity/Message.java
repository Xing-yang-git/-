package com.platform.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 聊天消息实体 — 服务端持久化，客户端 wx.Storage 作为展示缓存。
 */
@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 会话标识：IDLE_42_3_7 / HELP_42_3_7 / USER_3_7 */
    @Column(name = "session_id", nullable = false, length = 255)
    private String sessionId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    /** 消息正文；撤回后置为 NULL */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** text | system */
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private String messageType = "text";

    /** sent | delivered | read */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "sent";

    /** 撤回时间，NULL=未撤回 */
    @Column(name = "recalled_at")
    private LocalDateTime recalledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", insertable = false, updatable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", insertable = false, updatable = false)
    private User toUser;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
