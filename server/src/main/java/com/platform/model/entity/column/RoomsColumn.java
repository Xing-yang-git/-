package com.platform.model.entity.column;

/**
 * rooms 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 rooms 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class RoomsColumn {

    /** 工具类，禁止实例化 */
    private RoomsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "rooms";

    /** 房间 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属单元 ID，外键 → units.id */
    public static final String COL_UNIT_ID = "unit_id";
    /** 房间号（如 "1502"） */
    public static final String COL_ROOM_NUMBER = "room_number";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
