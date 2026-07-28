package com.platform.model.entity.column;

/**
 * notifications 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 notifications 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class NotificationsColumn {

    /** 工具类，禁止实例化 */
    private NotificationsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "notifications";

    /** 通知 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 目标用户 ID，外键 → users.id */
    public static final String COL_USER_ID = "user_id";
    /** 通知类型：borrow_request / borrow_result / help_application / help_result / audit_result / violation / return_confirm / notification */
    public static final String COL_TYPE = "type";
    /** 通知标题 */
    public static final String COL_TITLE = "title";
    /** 通知内容 */
    public static final String COL_CONTENT = "content";
    /** 关联业务 ID（如借用记录 ID、帮助申请 ID） */
    public static final String COL_RELATED_ID = "related_id";
    /** 是否已读 */
    public static final String COL_IS_READ = "is_read";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
