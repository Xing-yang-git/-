package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdleItemDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userRoom;
    private String postType;
    private String title;
    private String description;
    private String category;
    private String condition;
    private BigDecimal price;
    private String images;
    private Integer maxDuration;
    private String durationUnit;
    private String pickupMethod;
    private String status;
    private String delistReason;
    private Boolean isProxy;
    private LocalDateTime createdAt;
    private Double rating;
    private Long lendCount;
    private String returnRate;
    // 「以往记录」弹层五项统计的其余三项（口径见 UserActivityService.interactionStats）
    private Long borrowCount;
    private Long helpCount;
    private Long helpedCount;

    // 当前用户对该物品的借用申请状态（null = 未申请）
    private String userBorrowStatus;  // null | "pending" | "approved" | "returned"
}
