package com.platform.model.dto;

import lombok.Data;

@Data
public class ReturnRequest {
    private String returnStatus;
    private String returnNote;
    private String damageType;
    private String damageNote;
    private Boolean isOnTime;
    private String returnPhotos;
}
