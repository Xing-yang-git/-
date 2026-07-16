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
public class BorrowResponseDTO {
    private Long id;
    private Long idleId;
    private String idleTitle;
    private String itemImage;
    private Long ownerId;
    private String ownerName;
    private Long borrowerId;
    private String borrowerName;
    private String durationType;
    private Integer durationDays;
    private String note;
    private String status;
    private String returnStatus;
    private String damageType;
    private Boolean isOnTime;
    private String returnPhotos;
    private LocalDateTime createdAt;
}
