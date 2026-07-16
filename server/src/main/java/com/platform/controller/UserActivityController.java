package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.UserActivityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/user")
public class UserActivityController {

    private final UserActivityService userActivityService;

    public UserActivityController(UserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    /**
     * 当前用户个人资料 — 我的 tab。
     * 一次调用返回用户信息、评分和统计数据。
     */
    @GetMapping("/profile")
    public Result<?> profile(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(userActivityService.getProfile(userId));
    }

    /**
     * 我发布的帖子 — 发布 tab。
     * @param status 可选过滤条件："online" | "offline" | "completed"
     */
    @GetMapping("/posts")
    public Result<?> myPosts(@RequestParam(required = false) String status, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(userActivityService.getMyPosts(userId, status));
    }

    /**
     * 待审批事项 — 审批 tab。
     * @param type "borrow" | "help"
     */
    @GetMapping("/approvals")
    public Result<?> approvals(@RequestParam String type, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(userActivityService.getApprovals(userId, type));
    }

    /**
     * 进行中的交易 — 进行中 tab。
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    @GetMapping("/in-progress")
    public Result<?> inProgress(@RequestParam String role, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(userActivityService.getInProgress(userId, role));
    }

    /**
     * 已完成的交易 — 已完成 tab。
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    @GetMapping("/completed")
    public Result<?> completed(@RequestParam String role, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(userActivityService.getCompleted(userId, role));
    }
}
