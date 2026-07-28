package com.platform.model.entity.column;

/**
 * users 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 users 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class UsersColumn {

    /** 工具类，禁止实例化 */
    private UsersColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "users";

    /** 用户 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 房间 ID，外键 → rooms.id */
    public static final String COL_ROOM_ID = "room_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 微信 openid */
    public static final String COL_OPENID = "openid";
    /** 用户名 */
    public static final String COL_USERNAME = "username";
    /** 密码哈希（BCrypt） */
    public static final String COL_PASSWORD_HASH = "password_hash";
    /** 用户类型：owner(业主) / tenant(租户) / admin(管理员) / senior_admin / super_admin */
    public static final String COL_USER_TYPE = "user_type";
    /** 真实姓名 */
    public static final String COL_NAME = "name";
    /** 手机号（11 位） */
    public static final String COL_PHONE = "phone";
    /** 手机号是否已验证 */
    public static final String COL_PHONE_VERIFIED = "phone_verified";
    /** 头像 URL */
    public static final String COL_AVATAR_URL = "avatar_url";
    /** 认证状态：pending(待审核) / approved(已通过) / rejected(已驳回) */
    public static final String COL_AUTH_STATUS = "auth_status";
    /** 封禁原因 */
    public static final String COL_BANNED_REASON = "banned_reason";
    /** 认证材料图片（JSON 数组） */
    public static final String COL_DOC_IMAGES = "doc_images";
    /** 驳回原因 */
    public static final String COL_REJECT_REASON = "reject_reason";
    /** Token 版本号（C端单会话登录控制，每次登录自增） */
    public static final String COL_TOKEN_VERSION = "token_version";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
