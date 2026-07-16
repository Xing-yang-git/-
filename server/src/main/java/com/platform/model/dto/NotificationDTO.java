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
public class NotificationDTO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
