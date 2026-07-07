package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private UUID id;
    private UUID sessionId;
    private UUID senderId;
    private String senderName;
    private String content;
    private String messageType;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
