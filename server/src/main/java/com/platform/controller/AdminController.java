package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.service.AdminService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ===== 数据看板 =====
    @GetMapping("/dashboard")
    public Result<?> dashboard() {
        return Result.ok(adminService.getDashboard());
    }

    // ===== 住户审核 =====
    @GetMapping("/audits")
    public Result<?> audits(@RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.getAudits(status, page, size));
    }

    @GetMapping("/audits/counts")
    public Result<?> auditCounts() {
        return Result.ok(adminService.getAuditCounts());
    }

    @PutMapping("/audits/{userId}")
    public Result<?> auditUser(@PathVariable UUID userId, @RequestBody AuditRequest req,
                               Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        adminService.auditUser(adminId, userId, req);
        return Result.ok();
    }

    // ===== 内容管理 =====
    @GetMapping("/content")
    public Result<?> contentList(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String building,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.getContentList(status, type, building, search, page, size));
    }

    @GetMapping("/content/counts")
    public Result<?> contentCounts() {
        return Result.ok(adminService.getContentCounts());
    }

    @GetMapping("/content/{id}")
    public Result<?> contentDetail(@PathVariable UUID id, @RequestParam String type) {
        return Result.ok(adminService.getContentDetail(id, type));
    }

    @PutMapping("/content/{id}/offline")
    public Result<?> offlineContent(@PathVariable UUID id, @RequestBody ContentOfflineRequest req,
                                     Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        return Result.ok(adminService.removeContent(adminId, id, req));
    }

    // ===== 楼栋列表 =====
    @GetMapping("/buildings")
    public Result<?> buildings() {
        return Result.ok(adminService.getBuildings());
    }

    // ===== 住户检索 =====
    @GetMapping("/residents/search")
    public Result<?> searchResidents(@RequestParam(required = false) String building,
                                      @RequestParam(required = false) String unit,
                                      @RequestParam(required = false) String room,
                                      @RequestParam(required = false) String userType,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return Result.ok(adminService.searchResidents(building, unit, room, userType, keyword, page, size));
    }

    // ===== 物业代发 =====
    @PostMapping("/proxy/idle")
    public Result<?> proxyPublishIdle(@RequestBody IdleItemRequest req, Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        return Result.ok(adminService.proxyPublishIdle(adminId, req));
    }

    @PostMapping("/proxy/help")
    public Result<?> proxyPublishHelp(@RequestBody HelpRequestDTO req, Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        return Result.ok(adminService.proxyPublishHelp(adminId, req));
    }

    // ===== 互助记录 =====
    @GetMapping("/records")
    public Result<?> records(@RequestParam(defaultValue = "borrow") String type,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.getRecords(type, page, size));
    }

    // ===== 数据导出 =====
    @GetMapping("/export")
    public Result<?> export(@RequestParam(defaultValue = "borrow") String type) {
        return Result.ok(adminService.exportData(type));
    }

    // ===== 操作日志 =====
    @GetMapping("/logs")
    public Result<?> operationLogs(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.getOperationLogs(page, size));
    }

    // ===== 聊天记录调阅 =====
    @GetMapping("/chat-sessions")
    public Result<?> chatSessions(@RequestParam(required = false) String keyword) {
        return Result.ok(adminService.getChatSessions(keyword));
    }
}
