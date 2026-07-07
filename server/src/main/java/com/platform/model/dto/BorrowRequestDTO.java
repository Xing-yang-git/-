package com.platform.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class BorrowRequestDTO {
    private UUID idleId;
    private String durationType;
    private Integer durationDays;
    private String startDate;
    private String note;
}
