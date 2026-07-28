package com.platform.model.entity.column;

/**
 * help_requests 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 help_requests 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class HelpRequestsColumn {

    /** 工具类，禁止实例化 */
    private HelpRequestsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "help_requests";

    /** 求助 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 发布用户 ID，外键 → users.id */
    public static final String COL_USER_ID = "user_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 求助标题 */
    public static final String COL_TITLE = "title";
    /** 求助描述 */
    public static final String COL_DESCRIPTION = "description";
    /** 求助分类 */
    public static final String COL_CATEGORY = "category";
    /** 是否紧急求助 */
    public static final String COL_IS_URGENT = "is_urgent";
    /** 求助开始时间 */
    public static final String COL_TIME_START = "time_start";
    /** 求助结束时间 */
    public static final String COL_TIME_END = "time_end";
    /** 求助图片 URL 列表（JSON 数组） */
    public static final String COL_IMAGES = "images";
    /** 状态：online(展示中) / reserved(已有人接单) / completed(已完成) / offline(已下架)，引用 {@link com.platform.common.BizStatus} */
    public static final String COL_STATUS = "status";
    /** 下架原因 */
    public static final String COL_DELIST_REASON = "delist_reason";
    /** 是否为代发（管理员代住户发布） */
    public static final String COL_IS_PROXY = "is_proxy";
    /** 违规类型 */
    public static final String COL_VIOLATION_TYPE = "violation_type";
    /** 违规原因描述 */
    public static final String COL_VIOLATION_REASON = "violation_reason";
    /** 违规处理人 ID，外键 → users.id */
    public static final String COL_VIOLATED_BY = "violated_by";
    /** 违规处理时间 */
    public static final String COL_VIOLATED_AT = "violated_at";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
