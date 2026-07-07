package com.platform.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class HelpRequestDTO {
    private UUID userId;
    private String title;
    private String description;
    private String category;
    private Boolean isUrgent;
    private String timeStart;
    private String timeEnd;
    private String location;
    private String rewardType;
    private String images;
}
