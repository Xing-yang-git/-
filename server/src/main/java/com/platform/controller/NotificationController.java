package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<?> getNotifications(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(notificationService.getNotifications(userId));
    }

    @GetMapping("/unread-count")
    public Result<?> getUnreadCount(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/read-all")
    public Result<?> markAllRead(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        notificationService.markAllRead(userId);
        return Result.ok();
    }

    /** 清空当前用户全部通知 */
    @DeleteMapping("/all")
    public Result<?> deleteAll(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        notificationService.deleteAll(userId);
        return Result.ok();
    }
}
