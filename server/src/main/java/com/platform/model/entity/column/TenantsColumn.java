package com.platform.model.entity.column;

/**
 * tenants 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 tenants 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class TenantsColumn {

    /** 工具类，禁止实例化 */
    private TenantsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "tenants";

    /** 小区 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 小区名称 */
    public static final String COL_NAME = "name";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
