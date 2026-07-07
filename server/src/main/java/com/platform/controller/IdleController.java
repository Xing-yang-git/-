package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.IdleItemRequest;
import com.platform.service.IdleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/idle")
public class IdleController {

    private final IdleService idleService;

    public IdleController(IdleService idleService) {
        this.idleService = idleService;
    }

    @PostMapping
    public Result<?> publish(@RequestBody IdleItemRequest req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(idleService.publish(userId, req));
    }

    @GetMapping("/home")
    public Result<?> homeList(@RequestParam(defaultValue = "LEND") String postType,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(idleService.getHomeList(postType, page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable UUID id) {
        return Result.ok(idleService.getDetail(id));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword,
                             @RequestParam(defaultValue = "LEND") String postType,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size) {
        return Result.ok(idleService.search(keyword, postType, page, size));
    }

    @GetMapping("/my")
    public Result<?> myPosts(@RequestParam(defaultValue = "LEND") String postType,
                              Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(idleService.getMyPosts(userId, postType));
    }

    @PutMapping("/{id}/delist")
    public Result<?> delist(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        idleService.delist(userId, id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        idleService.deleteItem(userId, id);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable UUID id, @RequestBody IdleItemRequest req,
                            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(idleService.update(userId, id, req));
    }
}
