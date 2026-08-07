package com.platform.model.entity.column;

/**
 * agent_conversations 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 agent_conversations 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class AgentConversationsColumn {

    /** 工具类，禁止实例化 */
    private AgentConversationsColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "agent_conversations";

    /** 会话 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 住户用户 ID，外键 → users.id */
    public static final String COL_USER_ID = "user_id";
    /** 所属小区 ID，外键 → tenants.id */
    public static final String COL_TENANT_ID = "tenant_id";
    /** 会话级 conversation_id（滑动窗口多条归档记录共享同一会话 id；历史数据 NULL 视作自身 id） */
    public static final String COL_CONVERSATION_ID = "conversation_id";
    /** 会话标题（归档时由首条消息生成） */
    public static final String COL_TITLE = "title";
    /** 消息条数（归档阈值判断） */
    public static final String COL_MESSAGE_COUNT = "message_count";
    /** 状态：active(进行中)/archived(已归档)/deleted(已软删) */
    public static final String COL_STATUS = "status";
    /** 最后一条消息时间（空闲归档判断） */
    public static final String COL_LAST_MESSAGE_AT = "last_message_at";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
    /** 更新时间（审计） */
    public static final String COL_UPDATED_AT = "updated_at";
}
