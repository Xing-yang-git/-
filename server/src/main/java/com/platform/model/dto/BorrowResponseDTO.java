package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowResponseDTO {
    private UUID id;
    private UUID idleId;
    private String idleTitle;
    private String itemImage;
    private UUID ownerId;
    private String ownerName;
    private UUID borrowerId;
    private String borrowerName;
    private String durationType;
    private Integer durationDays;
    private LocalDate startDate;
    private String note;
    private String status;
    private String returnStatus;
    private String damageType;
    private Boolean isOnTime;
    private String returnPhotos;
    private LocalDateTime createdAt;
}
