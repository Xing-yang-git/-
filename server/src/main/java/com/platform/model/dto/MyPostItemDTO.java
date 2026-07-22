package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C端"我的发布"管理页的统一 DTO。
 * 覆盖：发布(online/offline)、审批、进行中、已完成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPostItemDTO {
    private Long id;
    private String type;            // "idle" | "help"
    private String subType;         // 进行中/已完成场景："borrow"|"lend"|"helpReq"|"helpPro"
    private String postType;        // "LEND" | "HELP"

    // 帖子字段（用于"发布" tab）
    private String title;
    private String category;
    private String description;
    private BigDecimal price;       // 仅闲置
    private String condition;       // 仅闲置
    private Integer maxDuration;
    private String durationUnit;
    private String pickupMethod;
    private Boolean isUrgent;       // 仅求助
    private Boolean isProxy;
    private String status;          // 数据库原始状态
    private String displayStatus;   // "在线"/"已下架"/"进行中"/"已完成"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 对方信息字段
    private Long personId;          // 对方用户ID（用于点击跳转聊天）
    private String personName;      // "3栋2单元1502号(业主)"
    private String personRoom;      // "3栋2单元1502号"
    private String personType;      // "业主"/"租客"
    private Double personRating;

    // 用户统计数据（用于审批详情弹层）
    private Integer borrowCount;
    private Double borrowReturnRate;
    private Integer lendCount;
    private Integer helpReqCount;
    private Integer helpProCount;

    // 申请说明 (借用说明/借入说明/求助说明)
    private String note;

    // 求助时间范围（仅 HELP 使用，格式 "yyyy-MM-dd HH:mm"）
    private String timeStart;
    private String timeEnd;

    // 进行中字段
    private Integer remainingDays;
    private Integer remainingHours;
    private Integer expectedReturnDays;
    private Boolean isOverdue;
    private String roleLabel;       // "借出住户"/"借走住户"/"帮助住户"/"求助住户"
    private String metaText;        // "剩余 3 天归还"

    // 已完成字段
    private LocalDateTime completedAt;
    private Double myRating;
    private String myFeedback;
    private Double theirRating;
    private String theirFeedback;
}
