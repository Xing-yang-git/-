package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.HelpRequestDTO;
import com.platform.service.HelpService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/help")
public class HelpController {

    private final HelpService helpService;

    public HelpController(HelpService helpService) {
        this.helpService = helpService;
    }

    @PostMapping
    public Result<?> publish(@RequestBody HelpRequestDTO req, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.publish(userId, req));
    }

    @GetMapping("/home")
    public Result<?> homeList(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size) {
        return Result.ok(helpService.getHomeList(page, size));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable UUID id) {
        return Result.ok(helpService.getDetail(id));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String keyword,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size) {
        return Result.ok(helpService.search(keyword, page, size));
    }

    @GetMapping("/my")
    public Result<?> myPosts(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.getMyPosts(userId));
    }

    @PutMapping("/{id}/delist")
    public Result<?> delist(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        helpService.delist(userId, id);
        return Result.ok();
    }

    @PostMapping("/{id}/apply")
    public Result<?> apply(@PathVariable UUID id, @RequestBody Map<String, String> body,
                           Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        helpService.apply(userId, id, body.getOrDefault("note", ""));
        return Result.ok();
    }

    @PutMapping("/applications/{appId}/approve")
    public Result<?> approveApplication(@PathVariable UUID appId, @RequestBody ApproveRequest req,
                                        Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        helpService.approveReject(userId, appId, req);
        return Result.ok();
    }

    @GetMapping("/applications/my")
    public Result<?> myApplications(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.getMyApplications(userId));
    }

    @GetMapping("/applications/pending")
    public Result<?> pendingApprovals(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.getPendingApprovals(userId));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable UUID id, @RequestBody HelpRequestDTO req,
                            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.update(userId, id, req));
    }

    @PutMapping("/applications/{appId}/complete")
    public Result<?> completeHelp(@PathVariable UUID appId, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return Result.ok(helpService.completeHelp(userId, appId));
    }
}
