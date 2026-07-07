package com.platform.model.dto;

import lombok.Data;

@Data
public class WxLoginRequest {
    private String code;
    private String name;
    private String phone;
    private String avatarUrl;
}
