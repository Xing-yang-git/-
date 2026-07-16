package com.platform.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegisterRequest {
    private Long tenantId;
    private String building;
    private String unit;
    private String room;
    private String name;
    private String phone;
    private String password;
    private String userType;
    private List<String> docImages;
}
