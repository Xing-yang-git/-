package com.platform.model.entity.column;

/**
 * borrow_requests 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 borrow_requests 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class BorrowRequestsColumn {

    /** 工具类，禁止实例化 */
    private BorrowRequestsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "borrow_requests";

    /** 借用记录 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 被借用物品 ID，外键 → idle_items.id */
    public static final String COL_IDLE_ID = "idle_id";
    /** 借入方用户 ID，外键 → users.id */
    public static final String COL_BORROWER_ID = "borrower_id";
    /** 借出时长类型：day(按天) / hour(按小时) */
    public static final String COL_DURATION_TYPE = "duration_type";
    /** 借出天数 */
    public static final String COL_DURATION_DAYS = "duration_days";
    /** 借用备注 */
    public static final String COL_NOTE = "note";
    /** 借出开始日期 */
    public static final String COL_START_DATE = "start_date";
    /** 借用状态：pending(待审批) / approved(已同意) / rejected(已拒绝) / returned(已归还) */
    public static final String COL_STATUS = "status";
    /** 交接照片 URL 列表（JSON 数组） */
    public static final String COL_HANDOFF_PHOTOS = "handoff_photos";
    /** 审批通过时间（物主同意借出时设置） */
    public static final String COL_APPROVED_AT = "approved_at";
    /** 归还完成时间 */
    public static final String COL_RETURNED_AT = "returned_at";
    /** 归还状态：ontime(按时) / delayed(逾期) / not_returned(未归还)，引用 {@link com.platform.common.ReturnStatus} */
    public static final String COL_RETURN_STATUS = "return_status";
    /** 归还备注 */
    public static final String COL_RETURN_NOTE = "return_note";
    /** 损坏类型：normal(正常损耗) / severe(非正常损坏) / broken(完全损坏)，引用 {@link com.platform.common.DamageType} */
    public static final String COL_DAMAGE_TYPE = "damage_type";
    /** 损坏备注 */
    public static final String COL_DAMAGE_NOTE = "damage_note";
    /** 是否按时归还（null 表示尚未归还） */
    public static final String COL_IS_ON_TIME = "is_on_time";
    /** 归还照片 URL 列表（JSON 数组） */
    public static final String COL_RETURN_PHOTOS = "return_photos";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
