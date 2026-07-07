package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.CommonService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/common")
public class CommonController {

    private final CommonService commonService;

    public CommonController(CommonService commonService) {
        this.commonService = commonService;
    }

    @GetMapping("/tenants")
    public Result<?> getTenants() {
        return Result.ok(commonService.getAllTenants());
    }

    @GetMapping("/buildings")
    public Result<?> getBuildings(@RequestParam UUID tenantId) {
        return Result.ok(commonService.getBuildingsByTenantId(tenantId));
    }

    @GetMapping("/units")
    public Result<?> getUnits(@RequestParam UUID buildingId) {
        return Result.ok(commonService.getUnitsByBuildingId(buildingId));
    }

    @GetMapping("/rooms")
    public Result<?> getRooms(@RequestParam UUID unitId) {
        return Result.ok(commonService.getRoomsByUnitId(unitId));
    }

    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        String url = commonService.uploadFile(file);
        return Result.ok(java.util.Map.of("url", url));
    }
}
