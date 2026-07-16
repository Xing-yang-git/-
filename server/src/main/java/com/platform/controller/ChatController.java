package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.ChatService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 发送聊天消息 — 服务端仅通过 WebSocket 转发，不做持久化。
     */
    @PostMapping("/relay")
    public Result<?> relay(@RequestBody Map<String, Object> body, Authentication auth) {
        Long fromUserId = Long.valueOf(auth.getName());
        Long toUserId = Long.valueOf(body.get("toUserId").toString());
        String content = (String) body.get("content");
        String messageType = (String) body.getOrDefault("messageType", "text");
        String sessionId = body.get("sessionId") != null ? body.get("sessionId").toString() : null;

        chatService.relayMessage(fromUserId, toUserId, content, messageType, sessionId);
        return Result.ok();
    }
}
