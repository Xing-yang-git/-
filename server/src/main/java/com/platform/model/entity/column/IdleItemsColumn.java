package com.platform.model.entity.column;

/**
 * idle_items 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 idle_items 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class IdleItemsColumn {

    /** 工具类，禁止实例化 */
    private IdleItemsColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "idle_items";

    /** 物品 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 发布用户 ID，外键 → users.id */
    public static final String COL_USER_ID = "user_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 发布类型：LEND(出借) / WANTED(求借)，引用 {@link com.platform.common.PostType} */
    public static final String COL_POST_TYPE = "post_type";
    /** 物品标题 */
    public static final String COL_TITLE = "title";
    /** 物品描述 */
    public static final String COL_DESCRIPTION = "description";
    /** 物品分类 */
    public static final String COL_CATEGORY = "category";
    /** 物品成色：like-new(几乎全新) / normal(正常) / worn(有磨损)，引用 {@link com.platform.common.BizStatus} */
    public static final String COL_CONDITION = "condition";
    /** 价格（元） */
    public static final String COL_PRICE = "price";
    /** 物品图片 URL 列表（JSON 数组） */
    public static final String COL_IMAGES = "images";
    /** 单次最多借出天数 */
    public static final String COL_MAX_DURATION = "max_duration";
    /** 借出时长单位：day(天) / week(周) / month(月) */
    public static final String COL_DURATION_UNIT = "duration_unit";
    /** 取货方式：self_pickup(自取) / express(快递) */
    public static final String COL_PICKUP_METHOD = "pickup_method";
    /** 状态：online(展示中) / reserved(已预订) / offline(已下架) / deleted(已删除)，引用 {@link com.platform.common.BizStatus} */
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
