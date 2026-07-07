package com.platform.model.dto;

import lombok.Data;

@Data
public class AuditRequest {
    private Boolean approved;
    private String reason;
}
