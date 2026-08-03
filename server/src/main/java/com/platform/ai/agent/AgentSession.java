package com.platform.ai.agent;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 热会话（Redis 存储）— 单设备单会话，key = agent:session:{userId}。
 *
 * <p>conversationId 关联 PG 归档会话（归档时写入）；messages 为多轮对话上下文
 * （含 user/assistant/tool 角色，恢复时可重建 LLM messages 序列）。</p>
 */
@Data
public class AgentSession {

    /** 关联的 PG 归档会话 ID（首次归档后回填；未归档为 null） */
    private Long conversationId;

    /** 会话消息（role/content/sources/actions） */
    private List<AgentMessageItem> messages = new ArrayList<>();

    /** 最近活跃时间（append 时更新，供空闲归档调度判断） */
    private LocalDateTime lastActive;

    /** 会话消息单条 */
    public record AgentMessageItem(String role, String content, String sources, String actions) {
    }
}
