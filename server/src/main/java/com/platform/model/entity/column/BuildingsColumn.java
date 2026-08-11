package com.platform.model.entity.column;

/**
 * buildings 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 buildings 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class BuildingsColumn {

    /** 工具类，禁止实例化 */
    private BuildingsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "buildings";

    /** 楼栋 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 楼栋号（数值，如 3；展示时拼 "栋" 后缀） */
    public static final String COL_BUILDING_NO = "building_no";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
