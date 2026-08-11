package com.platform.model.entity.column;

/**
 * units 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 units 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class UnitsColumn {

    /** 工具类，禁止实例化 */
    private UnitsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "units";

    /** 单元 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属楼栋 ID，外键 → buildings.id */
    public static final String COL_BUILDING_ID = "building_id";
    /** 单元号（数值，如 2；展示时拼 "单元" 后缀） */
    public static final String COL_UNIT_NO = "unit_no";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
