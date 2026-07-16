package com.platform.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.model.dto.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    // 一个用户可能有多个连接（如重连未及时关闭的旧连接），按用户维护 session 集合
    private static final Map<String, CopyOnWriteArraySet<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("Chat WebSocket connected: userId={}", userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserId(session);
        if (userId == null) return;

        WebSocketMessage msg = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

        if ("chat_message".equals(msg.getType())) {
            // 将消息转发给目标用户（其所有已打开的 session）
            WebSocketMessage response = new WebSocketMessage();
            response.setType("chat_message");
            response.setSessionId(msg.getSessionId());
            response.setFromUserId(userId);
            response.setContent(msg.getContent());
            response.setMessageType(msg.getMessageType() != null ? msg.getMessageType() : "text");
            sendToUser(msg.getToUserId(), response);

            // 回显给发送方作为送达确认
            WebSocketMessage echo = new WebSocketMessage();
            echo.setType("chat_message");
            echo.setSessionId(msg.getSessionId());
            echo.setFromUserId(userId);
            echo.setContent(msg.getContent());
            echo.setMessageType(msg.getMessageType() != null ? msg.getMessageType() : "text");
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(echo)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        if (userId != null) {
            CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                }
            }
            log.info("Chat WebSocket disconnected: userId={}", userId);
        }
    }

    public void sendToUser(String userId, WebSocketMessage message) {
        CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                } catch (IOException e) {
                    log.error("Failed to send message to userId={}", userId, e);
                }
            }
        }
    }

    public boolean isUserOnline(String userId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return false;
        return sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** 踢下线：关闭该用户全部 WS 连接（新登录挤出旧设备时由 AuthService 调用） */
    public void closeUserSessions(String userId) {
        CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return;
        for (WebSocketSession session : sessions) {
            try {
                session.close(new CloseStatus(4001, "kicked by new login"));
            } catch (IOException e) {
                log.warn("Failed to close session for userId={}", userId, e);
            }
        }
        userSessions.remove(userId);
    }

    /** 握手拦截器已完成鉴权，userId 从 session attributes 取（以 token 为准） */
    private String getUserId(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v != null ? v.toString() : null;
    }
}
