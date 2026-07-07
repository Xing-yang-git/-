package com.platform.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class IdleItemRequest {
    private UUID userId;
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
