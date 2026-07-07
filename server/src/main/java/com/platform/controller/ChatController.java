package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.ChatService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/sessions")
    public Result<?> getSessions(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(chatService.getSessions(userId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<?> getMessages(@PathVariable UUID sessionId) {
        return Result.ok(chatService.getMessages(sessionId));
    }

    @PutMapping("/sessions/{sessionId}/read")
    public Result<?> markRead(@PathVariable UUID sessionId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        chatService.markRead(sessionId, userId);
        return Result.ok();
    }
}
