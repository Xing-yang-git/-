package com.platform.model.entity.column;

/**
 * ratings 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 ratings 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class RatingsColumn {

    /** 工具类，禁止实例化 */
    private RatingsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "ratings";

    /** 评价 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 关联借用记录 ID，外键 → borrow_requests.id */
    public static final String COL_BORROW_ID = "borrow_id";
    /** 关联帮助申请 ID，外键 → help_applications.id */
    public static final String COL_HELP_APPLICATION_ID = "help_application_id";
    /** 评价方用户 ID，外键 → users.id */
    public static final String COL_FROM_USER_ID = "from_user_id";
    /** 被评价方用户 ID，外键 → users.id */
    public static final String COL_TO_USER_ID = "to_user_id";
    /** 评分（1-5 星） */
    public static final String COL_SCORE = "score";
    /** 评价反馈文字 */
    public static final String COL_FEEDBACK = "feedback";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
