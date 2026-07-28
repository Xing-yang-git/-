package com.platform.controller;

import com.platform.common.Result;
import com.platform.model.dto.*;
import com.platform.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * B端管理后台 REST API — 仪表盘、用户审核、内容管理、数据导出、系统配置。
 *
 * <p>提供 PC 管理端的全部后台接口：
 * <ul>
 *   <li>仪表盘数据（住户数、帖子数、借用/帮助统计、违规统计）</li>
 *   <li>用户审核（待审核/已通过/已驳回列表，审批操作）</li>
 *   <li>内容管理（闲置/求助列表、详情、下架）</li>
 *   <li>数据导出（住户/帖子/借用/帮助/下架/评价的 Excel 导出）</li>
 *   <li>记录查询（操作日志、导出历史）</li>
 *   <li>系统管理（管理员增删、小区/楼栋/单元/房间管理）</li>
 *   <li>代发功能（管理员以住户身份发布闲置/求助）</li>
 * </ul>
 *
 * <p>所有接口需管理员或超级管理员权限。</p>
 */
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
                            @RequestParam(defaultValue = "10") int size,
                            Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getAudits(adminId, status, page, size));
    }

    @GetMapping("/audits/counts")
    public Result<?> auditCounts(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getAuditCounts(adminId));
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
    public Result<?> contentCounts(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getContentCounts(adminId));
    }

    @GetMapping("/content/{id}")
    public Result<?> contentDetail(@PathVariable Long id, @RequestParam String type,
                                    Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getContentDetail(adminId, id, type));
    }

    @PutMapping("/content/{id}/offline")
    public Result<?> offlineContent(@PathVariable Long id, @Valid @RequestBody ContentOfflineRequest req,
                                     Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.removeContent(adminId, id, req));
    }

    // ===== 小区列表（super_admin 创建管理员时选择目标小区） =====
    @GetMapping("/tenants")
    public Result<?> tenants() {
        return Result.ok(adminService.getAllTenants());
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
        Long tenantId = body.get("tenantId") != null ? Long.valueOf(body.get("tenantId")) : null;
        String userType = body.getOrDefault("userType", "admin");
        return Result.ok(adminService.createAdmin(adminId,
                body.get("name"), body.get("phone"), body.get("password"), tenantId, userType));
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
                                      @RequestParam(defaultValue = "50") int size,
                                      Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.searchResidents(adminId, building, unit, room, userType, keyword, page, size));
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

    /**
     * 执行数据导出，生成多 Sheet 的 Excel 文件并返回二进制流。
     * 请求体包含勾选项目列表、日期范围和导出格式。
     */
    @PostMapping("/exports")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportRequest req,
                                         Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        byte[] excelBytes = adminService.exportData(adminId, req);
        // 文件名格式：{小区名}_{导出日期yyyyMMdd}.xlsx
        String tenantName = adminService.getTenantName(adminId);
        String exportDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = tenantName + "_" + exportDate + ".xlsx";
        // RFC 5987 编码，支持中文文件名
        String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * 查询导出日志（按时间倒序，仅返回当前管理员所属小区的记录）。
     */
    @GetMapping("/exports/logs")
    public Result<?> exportLogs(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getExportLogs(adminId, page, size));
    }

    // ===== 操作日志 =====
    @GetMapping("/logs")
    public Result<?> operationLogs(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        return Result.ok(adminService.getOperationLogs(adminId, page, size));
    }

    /** 导出操作日志为 Excel 文件 */
    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> exportOperationLogs(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        byte[] bytes = adminService.exportOperationLogs(adminId);
        String filename = "操作日志_" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

}
