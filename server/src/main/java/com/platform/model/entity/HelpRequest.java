package com.platform.model.entity;

import com.platform.common.BizStatus;
import com.platform.model.entity.column.HelpRequestsColumn;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 求助信息实体，对应 help_requests 表。
 *
 * <p>社区成员可发布求助信息（如拼车、代取快递、维修求助等），其他成员可申请接单。
 * 求助状态流转：online（展示中）→ reserved（已有人接单）→ completed（已完成）/ offline（已下架）。
 * 支持标记紧急求助（isUrgent），可设置求助时间范围。</p>
 */
@Entity
@Table(name = HelpRequestsColumn.TABLE_NAME)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HelpRequest {

    /** 求助 ID（自增主键） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 发布用户 ID，外键 → users.id */
    @Column(name = HelpRequestsColumn.COL_USER_ID, nullable = false)
    private Long userId;

    /** 所属小区 ID，外键 → tenants.id */
    @Column(name = HelpRequestsColumn.COL_TENANT_ID, nullable = false)
    private Long tenantId;

    /** 求助标题 */
    @Column(name = HelpRequestsColumn.COL_TITLE, nullable = false, length = 100)
    private String title;

    /** 求助描述 */
    @Column(name = HelpRequestsColumn.COL_DESCRIPTION, length = 200)
    private String description;

    /** 求助分类 */
    @Column(name = HelpRequestsColumn.COL_CATEGORY, nullable = false, length = 20)
    private String category;

    /** 是否紧急求助 */
    @Column(name = HelpRequestsColumn.COL_IS_URGENT, nullable = false)
    @Builder.Default
    private Boolean isUrgent = false;

    /** 求助开始时间（可选，表示求助的有效期起点） */
    @Column(name = HelpRequestsColumn.COL_TIME_START)
    private LocalDateTime timeStart;

    /** 求助结束时间（可选，表示求助的有效期终点） */
    @Column(name = HelpRequestsColumn.COL_TIME_END)
    private LocalDateTime timeEnd;

    /** 求助图片 URL 列表（JSON 数组字符串） */
    @Column(name = HelpRequestsColumn.COL_IMAGES, columnDefinition = "TEXT")
    private String images;

    /** 状态：online(展示中) / reserved(已预订) / completed(已完成) / offline(已下架)，引用 {@link BizStatus} */
    @Column(name = HelpRequestsColumn.COL_STATUS, nullable = false, length = 20)
    @Builder.Default
    private String status = BizStatus.ONLINE;

    /** 下架原因 */
    @Column(name = HelpRequestsColumn.COL_DELIST_REASON, length = 200)
    private String delistReason;

    /** 是否为代发（管理员代住户发布） */
    @Column(name = HelpRequestsColumn.COL_IS_PROXY, nullable = false)
    @Builder.Default
    private Boolean isProxy = false;

    /** 违规类型 */
    @Column(name = HelpRequestsColumn.COL_VIOLATION_TYPE, length = 20)
    private String violationType;

    /** 违规原因描述 */
    @Column(name = HelpRequestsColumn.COL_VIOLATION_REASON, length = 200)
    private String violationReason;

    /** 违规处理人 ID，外键 → users.id */
    @Column(name = HelpRequestsColumn.COL_VIOLATED_BY)
    private Long violatedBy;

    /** 违规处理时间 */
    @Column(name = HelpRequestsColumn.COL_VIOLATED_AT)
    private LocalDateTime violatedAt;

    /** 创建时间 */
    @Column(name = HelpRequestsColumn.COL_CREATED_AT, nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = HelpRequestsColumn.COL_UPDATED_AT, nullable = false)
    private LocalDateTime updatedAt;

    /** 关联发布用户实体（懒加载） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = HelpRequestsColumn.COL_USER_ID, insertable = false, updatable = false)
    private User user;

    /** 关联小区实体（懒加载） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = HelpRequestsColumn.COL_TENANT_ID, insertable = false, updatable = false)
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
