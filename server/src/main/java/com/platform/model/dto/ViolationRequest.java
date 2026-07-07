package com.platform.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ViolationRequest {
    private String targetType;
    private UUID targetId;
    private String violationType;
    private String reason;
}
