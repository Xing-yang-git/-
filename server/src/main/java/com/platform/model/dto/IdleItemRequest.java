package com.platform.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IdleItemRequest {
    private Long userId;
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
}
