package com.platform.model.dto;

import lombok.Data;

@Data
public class VerificationSubmitRequest {
    private String realName;
    private String idCard;
    private String idCardFront;
    private String idCardBack;
}
