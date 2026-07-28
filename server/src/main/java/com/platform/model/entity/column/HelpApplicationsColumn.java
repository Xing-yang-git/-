package com.platform.model.entity.column;

/**
 * help_applications 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 help_applications 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class HelpApplicationsColumn {

    /** 工具类，禁止实例化 */
    private HelpApplicationsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "help_applications";

    /** 申请 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 求助信息 ID，外键 → help_requests.id */
    public static final String COL_HELP_ID = "help_id";
    /** 接单用户 ID，外键 → users.id */
    public static final String COL_HELPER_ID = "helper_id";
    /** 申请备注 */
    public static final String COL_NOTE = "note";
    /** 申请状态：pending(待审批) / approved(已同意) / rejected(已拒绝) / completed(已完成) */
    public static final String COL_STATUS = "status";
    /** 完成时间 */
    public static final String COL_COMPLETED_AT = "completed_at";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
