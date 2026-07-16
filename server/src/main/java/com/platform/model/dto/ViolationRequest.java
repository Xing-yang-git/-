package com.platform.model.dto;

import lombok.Data;


@Data
public class ViolationRequest {
    private String targetType;
    private Long targetId;
    private String violationType;
    private String reason;
}
