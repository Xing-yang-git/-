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
    /** 消息在 DB 中的真实 id（服务端落库后回填），客户端乐观更新时用此 id 替换临时 id */
    private Long id;
    /** 消息创建时间（ISO 格式字符串），供客户端渲染时间戳 */
    private String createdAt;
    private String metric;
    private Object value;
    private String trigger;
}
