package com.platform.model.dto;

import com.platform.model.entity.Unit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单元数据 DTO — 用于 CommonController 楼栋-单元级联选择器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitDTO {

    /** 单元 ID */
    private Long id;

    /** 所属楼栋 ID */
    private Long buildingId;

    /** 单元号（数值，如 2；前端展示时拼 "2单元"） */
    private Integer unitNo;

    /** 从 Entity 转换 */
    public static UnitDTO from(Unit entity) {
        if (entity == null) return null;
        return new UnitDTO(entity.getId(), entity.getBuildingId(), entity.getUnitNo());
    }
}
