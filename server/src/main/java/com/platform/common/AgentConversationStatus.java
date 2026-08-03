package com.platform.common;

/**
 * Agent 会话状态常量 — agent_conversations.status 字段的唯一合法取值。
 *
 * <p>归档生命周期：active(Redis 热会话) → archived(已归档到 PG) → deleted(用户软删，保留审计)。</p>
 */
public final class AgentConversationStatus {

    /** 工具类，禁止实例化 */
    private AgentConversationStatus() {
    }

    /** 进行中（Redis 热会话，未归档） */
    public static final String ACTIVE = "active";

    /** 已归档（Redis TTL 过期或触发归档后，数据在 PG） */
    public static final String ARCHIVED = "archived";

    /** 已软删（用户删除，保留审计） */
    public static final String DELETED = "deleted";
}
