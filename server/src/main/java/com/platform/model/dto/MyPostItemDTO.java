package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified DTO for C端 my-posts management page.
 * Covers: 发布(online/offline), 审批, 进行中, 已完成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyPostItemDTO {
    private UUID id;
    private String type;            // "idle" | "help"
    private String subType;         // "borrow"|"lend"|"helpReq"|"helpPro" for in-progress/completed
    private String postType;        // "LEND" | "HELP"

    // Post fields (for 发布 tab)
    private String title;
    private String category;
    private String description;
    private BigDecimal price;       // idle only
    private String condition;       // idle only
    private Integer maxDuration;
    private String durationUnit;
    private String pickupMethod;
    private Boolean isUrgent;       // help only
    private Boolean isProxy;
    private String status;          // raw DB status
    private String displayStatus;   // "在线"/"已下架"/"进行中"/"已完成"
    private LocalDateTime createdAt;

    // Peer person fields
    private String personName;      // "3栋2单元1502号(业主)"
    private String personRoom;      // "3栋2单元1502号"
    private String personType;      // "业主"/"租客"
    private Double personRating;

    // User stats (for approval detail sheet)
    private Integer borrowCount;
    private Double borrowReturnRate;
    private Integer lendCount;
    private Integer helpReqCount;
    private Integer helpProCount;

    // In-progress fields
    private Integer remainingDays;
    private Integer expectedReturnDays;
    private Boolean isOverdue;
    private String roleLabel;       // "借出住户"/"借走住户"/"帮助住户"/"求助住户"
    private String metaText;        // "剩余 3 天归还"

    // Completed fields
    private LocalDateTime completedAt;
    private Double myRating;
    private String myFeedback;
    private Double theirRating;
    private String theirFeedback;
}
