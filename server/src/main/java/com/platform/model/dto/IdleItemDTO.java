package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdleItemDTO {
    private UUID id;
    private UUID userId;
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
}
