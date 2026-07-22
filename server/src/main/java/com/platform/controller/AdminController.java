package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ===== 数据看板 =====
    @GetMapping("/dashboard")
    public Result<?> dashboard(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getDashboard(adminId));
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
    public Result<?> auditUser(@PathVariable Long userId, @Valid @RequestBody AuditRequest req,
                               Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        adminService.auditUser(adminId, userId, req);
        return Result.ok();
    }

    // ===== 内容管理 =====
    @GetMapping("/content")
    public Result<?> contentList(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String building,
                                  @RequestParam(required = false) String unit,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getContentList(adminId, status, type, building, unit, search, page, size));
    }

    @GetMapping("/content/counts")
    public Result<?> contentCounts() {
        return Result.ok(adminService.getContentCounts());
    }

    @GetMapping("/content/{id}")
    public Result<?> contentDetail(@PathVariable Long id, @RequestParam String type) {
        return Result.ok(adminService.getContentDetail(id, type));
    }

    @PutMapping("/content/{id}/offline")
    public Result<?> offlineContent(@PathVariable Long id, @Valid @RequestBody ContentOfflineRequest req,
                                     Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.removeContent(adminId, id, req));
    }

    // ===== 社区数据（登录后一次性加载） =====
    @GetMapping("/community")
    public Result<?> community(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getCommunityData(adminId));
    }

    // ===== 楼栋列表 =====
    @GetMapping("/buildings")
    public Result<?> buildings(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getBuildings(adminId));
    }

    // ===== 个人信息 =====
    @PutMapping("/profile")
    public Result<?> updateProfile(@Valid @RequestBody Map<String, String> body, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.updateProfile(adminId, body.get("name")));
    }

    // ===== 修改密码 =====
    @PutMapping("/password")
    public Result<?> updatePassword(@Valid @RequestBody Map<String, String> body, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        adminService.updatePassword(adminId, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    // ===== 管理员账号管理（仅 super_admin） =====
    @GetMapping("/admins")
    public Result<?> admins(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getAdmins(adminId));
    }

    @PostMapping("/admins")
    public Result<?> createAdmin(@Valid @RequestBody Map<String, String> body, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.createAdmin(adminId,
                body.get("name"), body.get("phone"), body.get("password")));
    }

    @DeleteMapping("/admins/{id}")
    public Result<?> deleteAdmin(@PathVariable Long id, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        adminService.deleteAdmin(adminId, id);
        return Result.ok();
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
    public Result<?> proxyPublishIdle(@Valid @RequestBody IdleItemRequest req, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.proxyPublishIdle(adminId, req));
    }

    @PostMapping("/proxy/help")
    public Result<?> proxyPublishHelp(@Valid @RequestBody HelpRequestDTO req, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.proxyPublishHelp(adminId, req));
    }

    // ===== 互助记录 =====
    @GetMapping("/records")
    public Result<?> records(@RequestParam(defaultValue = "borrow") String type,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getRecords(adminId, type, page, size));
    }

    // ===== 数据导出 =====
    @GetMapping("/export")
    public Result<?> export(@RequestParam(defaultValue = "borrow") String type,
                            Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.exportData(adminId, type));
    }

    // ===== 操作日志 =====
    @GetMapping("/logs")
    public Result<?> operationLogs(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getOperationLogs(adminId, page, size));
    }

}
