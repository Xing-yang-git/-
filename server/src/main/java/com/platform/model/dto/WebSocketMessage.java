package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type;
    private String sessionId;
    private String toUserId;
    private String fromUserId;
    private String fromUserName;
    private String content;
    private String messageType;
    private String createdAt;
    private String metric;
    private Object value;
    private String trigger;
}
