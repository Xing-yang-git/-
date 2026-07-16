package com.platform.service;

import com.platform.model.entity.Building;
import com.platform.model.entity.Room;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.Unit;
import com.platform.repository.BuildingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

@Service
public class CommonService {

    /** 允许上传的图片扩展名白名单（含前导点，小写比较） */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final TenantRepository tenantRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public CommonService(TenantRepository tenantRepository,
                         BuildingRepository buildingRepository,
                         UnitRepository unitRepository,
                         RoomRepository roomRepository) {
        this.tenantRepository = tenantRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.roomRepository = roomRepository;
    }

    public List<Tenant> getAllTenants() {
        return tenantRepository.findAll();
    }

    public List<Building> getBuildingsByTenantId(Long tenantId) {
        return buildingRepository.findByTenantId(tenantId);
    }

    public List<Unit> getUnitsByBuildingId(Long buildingId) {
        return unitRepository.findByBuildingId(buildingId);
    }

    public List<Room> getRoomsByUnitId(Long unitId) {
        return roomRepository.findByUnitId(unitId);
    }

    public String uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }

        // 上传端点匿名可访问（注册流程需上传证件照）且 /uploads/** 被静态托管，
        // 必须用白名单挡住 html/svg 等可被浏览器执行的类型，防止存储型 XSS
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext)) {
            throw new RuntimeException("仅支持上传 jpg/jpeg/png/gif/webp 图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new RuntimeException("仅支持上传图片文件");
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String filename = java.util.UUID.randomUUID().toString() + ext;
            Path targetPath = uploadPath.resolve(filename);
            file.transferTo(targetPath.toFile());

            return "/uploads/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
}
