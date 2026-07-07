package com.platform.model.dto;

import lombok.Data;

@Data
public class ApproveRequest {
    private Boolean approved;
    private String reason;
}
