package com.platform.model.entity.column;

/**
 * operation_logs 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 operation_logs 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class OperationLogsColumn {

    /** 工具类，禁止实例化 */
    private OperationLogsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "operation_logs";

    /** 日志 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 操作管理员 ID，外键 → users.id */
    public static final String COL_ADMIN_ID = "admin_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 操作类型：approve_user / reject_user / remove_content / proxy_publish_idle / proxy_publish_help / create_admin / delete_admin */
    public static final String COL_ACTION = "action";
    /** 操作目标类型：idle / help / borrow / user */
    public static final String COL_TARGET_TYPE = "target_type";
    /** 操作目标 ID */
    public static final String COL_TARGET_ID = "target_id";
    /** 操作详情（JSON） */
    public static final String COL_DETAIL = "detail";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
