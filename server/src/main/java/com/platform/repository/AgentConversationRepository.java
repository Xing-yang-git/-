package com.platform.repository;

import com.platform.model.entity.AgentConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 会话归档数据访问层 — 历史列表、软删、恢复。
 */
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {

    /**
     * 查询用户未删除的会话（历史列表，按更新时间倒序）。
     *
     * @param userId   住户用户 ID
     * @param status   排除的状态（deleted）
     * @param pageable 分页参数
     * @return 历史会话分页
     */
    Page<AgentConversation> findByUserIdAndStatusNot(Long userId, String status, Pageable pageable);

    /**
     * 查询用户的全部归档/活动会话（含软删，管理员视角预留）。
     *
     * @param userId 住户用户 ID
     * @param status 会话状态
     * @return 会话列表
     */
    List<AgentConversation> findByUserIdAndStatus(Long userId, String status);

    /**
     * 按 ID + 归属校验查询（恢复/删除时防止越权操作他人会话）。
     *
     * @param id     会话 ID
     * @param userId 住户用户 ID
     * @return 会话，或 empty
     */
    Optional<AgentConversation> findByIdAndUserId(Long id, Long userId);
}
