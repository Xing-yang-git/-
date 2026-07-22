package com.platform.model.dto;

import lombok.Data;


@Data
public class RatingRequest {
    // 后端字段名
    private Long borrowId;
    private Long helpApplicationId;
    private Integer score;
    // C端小程序字段名（别名）
    private Long targetId;        // 对应 borrowId 或 helpApplicationId
    private String ratingType;    // "borrow" 或 "help"
    private Integer overallScore; // 对应 score
    private String feedback;      // 互助感想文本
}
