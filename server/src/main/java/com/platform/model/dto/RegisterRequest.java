package com.platform.model.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class RegisterRequest {
    private UUID tenantId;
    private String building;
    private String unit;
    private String room;
    private String name;
    private String phone;
    private String userType;
    private List<String> docImages;
}
