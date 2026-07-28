package com.platform.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IdleItemRequest {
    private Long userId;
    /** 是否为物业代发（管理员代为发布时设为 true） */
    private Boolean isProxy;
    private String postType;
    private String title;
    private String description;
    private String category;
    private String condition;
    /** 参考价格（元），必须大于 0，最多两位小数，最大 99,999,999.99 */
    @DecimalMin(value = "0.01", message = "价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "价格格式不正确")
    private BigDecimal price;
    private String images;
    private Integer maxDuration;
    private String durationUnit;
    private String pickupMethod;
}
