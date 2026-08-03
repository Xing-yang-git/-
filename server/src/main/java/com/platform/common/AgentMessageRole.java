package com.platform.common;

/**
 * Agent 消息角色常量 — agent_messages.role 与 Redis 热会话消息角色的唯一合法取值。
 *
 * <p>system prompt 动态构建不归档，故无 SYSTEM 值。</p>
 */
public final class AgentMessageRole {

    /** 工具类，禁止实例化 */
    private AgentMessageRole() {
    }

    /** 住户消息 */
    public static final String USER = "user";

    /** AI 回复 */
    public static final String ASSISTANT = "assistant";

    /** 工具调用结果（LLM tool calling 中间产物） */
    public static final String TOOL = "tool";
}
