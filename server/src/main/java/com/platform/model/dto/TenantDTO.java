package com.platform.model.dto;

import com.platform.model.entity.Tenant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小区/租户数据 DTO — 用于 CommonController 下拉选择器数据返回。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantDTO {

    /** 小区 ID */
    private Long id;

    /** 小区名称 */
    private String name;

    /** 从 Entity 转换 */
    public static TenantDTO from(Tenant entity) {
        if (entity == null) return null;
        return new TenantDTO(entity.getId(), entity.getName());
    }
}
