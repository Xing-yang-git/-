package com.platform.repository;

import com.platform.model.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Agent 消息归档数据访问层 — 会话消息读取（恢复上下文）。
 */
public interface AgentMessageRepository extends JpaRepository<AgentMessage, Long> {

    /**
     * 按会话读取全部消息（按 ID 升序，恢复上下文用）。
     *
     * @param conversationId 会话 ID
     * @return 消息列表
     */
    List<AgentMessage> findByConversationIdOrderByIdAsc(Long conversationId);
}
