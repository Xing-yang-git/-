package com.platform.model.entity.column;

/**
 * agent_messages 表字段名常量 — 与数据库 schema（db/alter.sql）严格一致。
 * <p>所有使用 agent_messages 表字段名的 JPA 注解必须引用本类常量，禁止硬编码字符串。</p>
 */
public final class AgentMessagesColumn {

    /** 工具类，禁止实例化 */
    private AgentMessagesColumn() {
    }

    /** 表名 */
    public static final String TABLE_NAME = "agent_messages";

    /** 消息 ID（自增主键） */
    public static final String COL_ID = "id";
    /** 所属会话 ID，外键 → agent_conversations.id */
    public static final String COL_CONVERSATION_ID = "conversation_id";
    /** 消息角色：user(住户)/assistant(AI)/tool(工具调用结果)，不归档 system */
    public static final String COL_ROLE = "role";
    /** 消息内容 */
    public static final String COL_CONTENT = "content";
    /** 引用来源（JSON 数组） */
    public static final String COL_SOURCES = "sources";
    /** 动作卡片（JSON 数组） */
    public static final String COL_ACTIONS = "actions";
    /** 文本审核结果：pending/pass/fail */
    public static final String COL_MODERATION_STATUS = "moderation_status";
    /** 违规原因（fail 时填充） */
    public static final String COL_MODERATION_REASON = "moderation_reason";
    /** 创建时间 */
    public static final String COL_CREATED_AT = "created_at";
}
