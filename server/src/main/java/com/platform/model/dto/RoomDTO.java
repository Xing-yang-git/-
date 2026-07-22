package com.platform.model.dto;

import com.platform.model.entity.Room;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房间数据 DTO — 用于 CommonController 单元-房间级联选择器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomDTO {

    /** 房间 ID */
    private Long id;

    /** 所属单元 ID */
    private Long unitId;

    /** 房间号（如 "1502"） */
    private String roomNumber;

    /** 从 Entity 转换 */
    public static RoomDTO from(Room entity) {
        if (entity == null) return null;
        return new RoomDTO(entity.getId(), entity.getUnitId(), entity.getRoomNumber());
    }
}
