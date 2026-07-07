package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.UserActivityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserActivityController {

    private final UserActivityService userActivityService;

    public UserActivityController(UserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    /**
     * My published posts — 发布 tab.
     * @param status optional filter: "online" | "offline" | "completed"
     */
    @GetMapping("/posts")
    public Result<?> myPosts(@RequestParam(required = false) String status, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(userActivityService.getMyPosts(userId, status));
    }

    /**
     * Pending approvals — 审批 tab.
     * @param type "borrow" | "help"
     */
    @GetMapping("/approvals")
    public Result<?> approvals(@RequestParam String type, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(userActivityService.getApprovals(userId, type));
    }

    /**
     * In-progress transactions — 进行中 tab.
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    @GetMapping("/in-progress")
    public Result<?> inProgress(@RequestParam String role, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(userActivityService.getInProgress(userId, role));
    }

    /**
     * Completed transactions — 已完成 tab.
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    @GetMapping("/completed")
    public Result<?> completed(@RequestParam String role, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(userActivityService.getCompleted(userId, role));
    }
}
