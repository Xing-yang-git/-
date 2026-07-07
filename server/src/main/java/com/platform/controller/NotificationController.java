package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<?> getNotifications(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(notificationService.getNotifications(userId));
    }

    @GetMapping("/unread-count")
    public Result<?> getUnreadCount(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/read-all")
    public Result<?> markAllRead(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        notificationService.markAllRead(userId);
        return Result.ok();
    }
}
