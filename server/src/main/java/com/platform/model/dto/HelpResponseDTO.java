package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HelpResponseDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userRoom;
    private String title;
    private String description;
    private String category;
    private Boolean isUrgent;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private String location;
    private String rewardType;
    private String images;
    private String status;
    private String delistReason;
    private Boolean isProxy;
    private int helperCount;
    private Long applicationId;
    private String applicationStatus;
    private Long helperId;
    private String helperName;
    private String applicationNote;
    private LocalDateTime createdAt;
    private Double rating;
    private Long helpCount;
    private Long helpedCount;
}
