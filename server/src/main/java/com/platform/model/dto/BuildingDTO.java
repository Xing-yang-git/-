package com.platform.model.dto;

import com.platform.model.entity.Building;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 楼栋数据 DTO — 用于 CommonController 楼栋-单元级联选择器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildingDTO {

    /** 楼栋 ID */
    private Long id;

    /** 所属小区 ID */
    private Long tenantId;

    /** 楼栋名称（如 "3栋"） */
    private String name;

    /** 从 Entity 转换 */
    public static BuildingDTO from(Building entity) {
        if (entity == null) return null;
        return new BuildingDTO(entity.getId(), entity.getTenantId(), entity.getName());
    }
}
