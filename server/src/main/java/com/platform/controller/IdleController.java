package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.IdleItemRequest;
import com.platform.service.IdleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/idle")
public class IdleController {

    private final IdleService idleService;

    public IdleController(IdleService idleService) {
        this.idleService = idleService;
    }

    @PostMapping
    public Result<?> publish(@RequestBody IdleItemRequest req, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(idleService.publish(userId, req));
    }

    @GetMapping("/home")
    public Result<?> homeList(@RequestParam(defaultValue = "LEND") String postType,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Authentication auth) {
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(idleService.getHomeList(postType, userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id, Authentication auth) {
        Long currentUserId = null;
        if (auth != null && auth.getName() != null) {
            try {
                currentUserId = Long.valueOf(auth.getName());
            } catch (IllegalArgumentException e) {
                // 忽略 — 视为未登录
            }
        }
        return Result.ok(idleService.getDetail(id, currentUserId));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword,
                             @RequestParam(defaultValue = "LEND") String postType,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Authentication auth) {
        // 传入当前用户以便 service 层按其所属小区做租户隔离
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(idleService.search(userId, keyword, postType, page, size));
    }

    @GetMapping("/my")
    public Result<?> myPosts(@RequestParam(defaultValue = "LEND") String postType,
                              Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(idleService.getMyPosts(userId, postType));
    }

    @PutMapping("/{id}/delist")
    public Result<?> delist(@PathVariable Long id, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        idleService.delist(userId, id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        idleService.deleteItem(userId, id);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody IdleItemRequest req,
                            Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(idleService.update(userId, id, req));
    }
}
