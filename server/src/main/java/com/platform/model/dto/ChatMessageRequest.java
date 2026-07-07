package com.platform.model.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class ChatMessageRequest {
    private UUID sessionId;
    private UUID toUserId;
    private String content;
    private String messageType;
}
