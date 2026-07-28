package com.platform.model.entity.column;

/**
 * export_logs 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 export_logs 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class ExportLogsColumn {

    /** 工具类，禁止实例化 */
    private ExportLogsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "export_logs";

    /** 导出记录 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 操作管理员 ID，外键 → users.id */
    public static final String COL_ADMIN_ID = "admin_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 导出格式：xlsx */
    public static final String COL_EXPORT_FORMAT = "export_format";
    /** 导出选项（JSON） */
    public static final String COL_SELECTED_OPTIONS = "selected_options";
    /** 导出数据起始日期 */
    public static final String COL_DATE_RANGE_START = "date_range_start";
    /** 导出数据截止日期 */
    public static final String COL_DATE_RANGE_END = "date_range_end";
    /** 导出的住户数量 */
    public static final String COL_RESIDENTS_COUNT = "residents_count";
    /** 导出的帖子数量 */
    public static final String COL_POSTS_COUNT = "posts_count";
    /** 导出的借用记录数量 */
    public static final String COL_BORROWS_COUNT = "borrows_count";
    /** 导出的帮助记录数量 */
    public static final String COL_HELPS_COUNT = "helps_count";
    /** 导出的下架记录数量 */
    public static final String COL_REMOVALS_COUNT = "removals_count";
    /** 导出的评价记录数量 */
    public static final String COL_RATINGS_COUNT = "ratings_count";
    /** 导出文件名 */
    public static final String COL_FILE_NAME = "file_name";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
