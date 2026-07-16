package com.platform.service;

import com.platform.model.dto.WebSocketMessage;
import com.platform.repository.UserRepository;
import com.platform.websocket.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final UserRepository userRepository;
    private final ChatWebSocketHandler webSocketHandler;

    public ChatService(UserRepository userRepository, ChatWebSocketHandler webSocketHandler) {
        this.userRepository = userRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 仅通过 WebSocket 将聊天消息转发给接收方。
     * 服务端不持久化 — 消息存储在用户设备本地。
     */
    public void relayMessage(Long fromUserId, Long toUserId, String content, String messageType, String sessionId) {
        var sender = userRepository.findById(fromUserId).orElse(null);
        String senderName = sender != null ? sender.getName() : "用户";

        WebSocketMessage msg = new WebSocketMessage();
        msg.setType("chat_message");
        msg.setSessionId(sessionId);
        msg.setFromUserId(fromUserId.toString());
        msg.setFromUserName(senderName);
        msg.setToUserId(toUserId.toString());
        msg.setContent(content);
        msg.setMessageType(messageType != null ? messageType : "text");
        msg.setCreatedAt(java.time.LocalDateTime.now().toString());

        webSocketHandler.sendToUser(toUserId.toString(), msg);
        log.debug("Relayed message from {} to {}", fromUserId, toUserId);
    }
}
