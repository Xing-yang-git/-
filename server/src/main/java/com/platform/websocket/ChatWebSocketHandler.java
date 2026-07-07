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

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        if (userId != null) {
            userSessions.put(userId, session);
            log.info("Chat WebSocket connected: userId={}", userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserId(session);
        if (userId == null) return;

        WebSocketMessage msg = objectMapper.readValue(message.getPayload(), WebSocketMessage.class);

        if ("chat_message".equals(msg.getType())) {
            // Forward message to target user
            WebSocketSession targetSession = userSessions.get(msg.getToUserId());
            if (targetSession != null && targetSession.isOpen()) {
                WebSocketMessage response = new WebSocketMessage();
                response.setType("chat_message");
                response.setSessionId(msg.getSessionId());
                response.setFromUserId(userId);
                response.setContent(msg.getContent());
                response.setMessageType(msg.getMessageType() != null ? msg.getMessageType() : "text");
                targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
            }

            // Echo back to sender as confirmation
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
            userSessions.remove(userId);
            log.info("Chat WebSocket disconnected: userId={}", userId);
        }
    }

    public void sendToUser(String userId, WebSocketMessage message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            } catch (IOException e) {
                log.error("Failed to send message to userId={}", userId, e);
            }
        }
    }

    public boolean isUserOnline(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    private String getUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2 && "userId".equals(pair[0])) {
                    return pair[1];
                }
            }
        }
        return null;
    }
}
