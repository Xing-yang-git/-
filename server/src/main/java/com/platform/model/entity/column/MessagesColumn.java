package com.platform.model.entity.column;

/**
 * messages 表字段名常量 — 与数据库 schema（db/schema.sql）严格一致。
 *
 * <p>所有使用 messages 表字段名的 JPA 注解（@Column、@JoinColumn、@UniqueConstraint）
 * 必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class MessagesColumn {

    /** 工具类，禁止实例化 */
    private MessagesColumn() {}

    /** 表名 */
    public static final String TABLE_NAME = "messages";

    /** 消息 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 会话 ID（用于标识同一对用户的聊天会话） */
    public static final String COL_SESSION_ID = "session_id";
    /** 发送方用户 ID，外键 → users.id */
    public static final String COL_FROM_USER_ID = "from_user_id";
    /** 接收方用户 ID，外键 → users.id */
    public static final String COL_TO_USER_ID = "to_user_id";
    /** 消息内容 */
    public static final String COL_CONTENT = "content";
    /** 消息类型：text(文本) / system(系统消息) */
    public static final String COL_MESSAGE_TYPE = "message_type";
    /** 消息状态：sent(已发送) / delivered(已送达) / read(已读) */
    public static final String COL_STATUS = "status";
    /** 撤回时间（null 表示未撤回） */
    public static final String COL_RECALLED_AT = "recalled_at";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间 */
    public static final String COL_UPDATED_AT = "updated_at";
}
