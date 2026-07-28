package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemDTO {
    private Long id;
    private String type;            // "idle" | "help"
    private String title;
    private String description;
    private List<String> images;    // 内容配图 URL 列表，可能为空
    private String category;
    private BigDecimal price;       // 仅闲置
    private String condition;       // 仅闲置
    private String publisherName;
    private String publisherRoom;   // "3栋2单元1502号(业主)"
    private String displayStatus;   // "展示中"|"进行中"|"已完成"|"已下架"
    private String rawStatus;       // 数据库实际状态值
    private Boolean isProxy;
    private Boolean isUrgent;       // 仅求助
    private LocalDateTime createdAt;

    // 对方信息（用于进行中/已完成）
    private String peerName;
    private String peerRoom;
    private Double peerRating;

    // 时间范围（求助：期望起止时间；闲置：由 BorrowRequest 推导的实际借用期）
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;

    // 借用时长（仅闲置：原帖设置的最长借出/借入时长）
    private Integer maxDuration;
    private String durationUnit;

    // 时间线节点（用于已完成项）：发布 → 申请 → 同意 → 完成
    private LocalDateTime applyAt;    // 借入/帮忙申请时间
    private LocalDateTime approveAt;  // 同意借出/帮忙时间
    private LocalDateTime completeAt; // 互助结束确认时间

    // 评价信息（用于已完成项）
    private String publisherRatingStars;
    private Double publisherRatingScore;
    private String publisherRatingComment;  // 求助方/借出方 互助感想（评价文字）
    private String peerRatingStars;
    private Double peerRatingScore;
    private String peerRatingComment;        // 相助方/借入方 互助感想（评价文字）

    // 违规信息（用于下架 tab）
    private String violationType;
    private String violationReason;
    private String violatorName;
    private LocalDateTime violatedAt;

    // 审批信息（用于待审批 tab）
    /** 审批人姓名（管理员审核后填充） */
    private String approverName;
    /** 申请人姓名（发布者，与 publisherName 相同，语义化别名） */
    private String applicantName;

    // 楼栋（用于筛选）
    private String building;
}
