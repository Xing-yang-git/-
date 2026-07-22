package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.HelpRequestDTO;
import com.platform.service.HelpService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/help-requests")
public class HelpController {

    private final HelpService helpService;

    public HelpController(HelpService helpService) {
        this.helpService = helpService;
    }

    @PostMapping
    public Result<?> publish(@Valid @RequestBody HelpRequestDTO req, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.publish(userId, req));
    }

    @GetMapping("/home")
    public Result<?> homeList(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Authentication auth) {
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(helpService.getHomeList(userId, page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.ok(helpService.getDetail(id));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             Authentication auth) {
        // 传入当前用户以便 service 层按其所属小区做租户隔离
        Long userId = auth != null ? Long.valueOf(auth.getName()) : null;
        return Result.ok(helpService.search(userId, keyword, page, size));
    }

    @GetMapping("/my")
    public Result<?> myPosts(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.getMyPosts(userId));
    }

    @PutMapping("/{id}/delist")
    public Result<?> delist(@PathVariable Long id, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        helpService.delist(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/apply")
    public Result<?> apply(@PathVariable Long id, @Valid @RequestBody Map<String, String> body,
                           Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        helpService.apply(userId, id, body.getOrDefault("note", ""));
        return Result.ok();
    }

    @PutMapping("/applications/{appId}/approve")
    public Result<?> approveApplication(@PathVariable Long appId, @Valid @RequestBody ApproveRequest req,
                                        Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        helpService.approveReject(userId, appId, req);
        return Result.ok();
    }

    @GetMapping("/applications/my")
    public Result<?> myApplications(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.getMyApplications(userId));
    }

    @GetMapping("/applications/pending")
    public Result<?> pendingApprovals(Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.getPendingApprovals(userId));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @Valid @RequestBody HelpRequestDTO req,
                            Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.update(userId, id, req));
    }

    @PutMapping("/applications/{appId}/complete")
    public Result<?> completeHelp(@PathVariable Long appId, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        return Result.ok(helpService.completeHelp(userId, appId));
    }
}
