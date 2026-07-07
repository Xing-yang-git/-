package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogDTO {
    private UUID id;
    private UUID adminId;
    private String adminName;
    private String action;
    private String targetType;
    private UUID targetId;
    private String detail;
    private LocalDateTime createdAt;
}
