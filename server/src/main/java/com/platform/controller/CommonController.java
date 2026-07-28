package com.platform.controller;

import com.platform.common.Result;
import com.platform.service.CommonService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公共资源 REST API — 小区/楼栋/单元/房间级联查询、文件上传。
 *
 * <p>提供无需认证的公共数据接口：
 * <ul>
 *   <li>空间层级数据（小区 → 楼栋 → 单元 → 房间）供注册和筛选使用</li>
 *   <li>图片和语音文件上传</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    private final CommonService commonService;

    public CommonController(CommonService commonService) {
        this.commonService = commonService;
    }

    /**
     * 获取全部小区列表。
     *
     * @return 小区列表
     */
    @GetMapping("/tenants")
    public Result<?> getTenants() {
        return Result.ok(commonService.getAllTenants());
    }

    /**
     * 获取指定小区下的全部楼栋。
     *
     * @param tenantId 小区 ID
     * @return 楼栋列表
     */
    @GetMapping("/buildings")
    public Result<?> getBuildings(@RequestParam Long tenantId) {
        return Result.ok(commonService.getBuildingsByTenantId(tenantId));
    }

    /**
     * 获取指定楼栋下的全部单元。
     *
     * @param buildingId 楼栋 ID
     * @return 单元列表
     */
    @GetMapping("/units")
    public Result<?> getUnits(@RequestParam Long buildingId) {
        return Result.ok(commonService.getUnitsByBuildingId(buildingId));
    }

    /**
     * 获取指定单元下的全部房间。
     *
     * @param unitId 单元 ID
     * @return 房间列表
     */
    @GetMapping("/rooms")
    public Result<?> getRooms(@RequestParam Long unitId) {
        return Result.ok(commonService.getRoomsByUnitId(unitId));
    }

    /**
     * 上传图片文件。
     *
     * @param file 图片文件（支持 jpg/png/gif/webp）
     * @return 上传后的文件访问 URL
     */
    @PostMapping("/upload")
    public Result<?> upload(@RequestParam("file") MultipartFile file) {
        String url = commonService.uploadFile(file);
        return Result.ok(java.util.Map.of("url", url));
    }

    /**
     * 上传语音文件 — 仅接受 mp3 / wav / aac / m4a 音频格式。
     *
     * @param file 音频文件
     * @return 上传后的文件访问 URL
     */
    @PostMapping("/upload-voice")
    public Result<?> uploadVoice(@RequestParam("file") MultipartFile file) {
        String url = commonService.uploadVoice(file);
        return Result.ok(java.util.Map.of("url", url));
    }
}
