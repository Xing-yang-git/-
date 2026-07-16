package com.platform.model.dto;

import lombok.Data;


@Data
public class BorrowRequestDTO {
    private Long idleId;
    private String durationType;
    private Integer durationDays;
    private String note;
}
