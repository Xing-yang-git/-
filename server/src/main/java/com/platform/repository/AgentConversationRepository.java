package com.platform.repository;

import com.platform.model.entity.AgentConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Agent 会话归档数据访问层 — 历史列表、软删、恢复。
 *
 * <p>滑动窗口改造后：归档按会话级 conversation_id 组织（多条归档行共享同一会话 id），
 * 恢复/软删均按会话级 id 解析，且强制携带 userId 防止越权操作他人会话。</p>
 */
public interface AgentConversationRepository extends JpaRepository<AgentConversation, Long> {

    /**
     * 查询用户未删除的会话（历史列表，分页）。
     *
     * @param userId   住户用户 ID
     * @param status   排除的状态（deleted）
     * @param pageable 分页参数
     * @return 历史会话分页
     */
    Page<AgentConversation> findByUserIdAndStatusNot(Long userId, String status, Pageable pageable);

    /**
     * 查询用户未删除的全部会话行（含软删排除；历史列表按会话分组用，滑动窗口同一会话多行）。
     *
     * @param userId 住户用户 ID
     * @param status 排除的状态（deleted）
     * @return 会话行列表
     */
    List<AgentConversation> findAllByUserIdAndStatusNot(Long userId, String status);

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

    /**
     * 按会话级 conversation_id 解析某用户的会话全部归档行（resume 恢复用）。
     *
     * <p>匹配规则：行 conversation_id = 传入值 或 行自身 id = 传入值（历史 NULL 数据视作自身 id），
     * 并强制 user_id 归属校验，防止越权恢复他人会话。
     * <b>注意</b>：必须用显式 JPQL 括号包裹 OR 子句——派生查询名 {@code findByUserIdAndConversationIdOrId}
     * 会生成 {@code (userId=? AND conversationId=?) OR (id=?)}，造成按 id 越权，故不采用派生命名。</p>
     *
     * @param userId         住户用户 ID
     * @param conversationId 会话级 id（历史数据传入归档行 id）
     * @param id             与 conversationId 同值（行自身 id 匹配）
     * @return 该会话的全部归档行（可能为空）
     */
    @Query("SELECT c FROM AgentConversation c WHERE c.userId = :userId " +
            "AND (c.conversationId = :conversationId OR c.id = :id)")
    List<AgentConversation> findOwnedByConversationIdOrId(@Param("userId") Long userId,
                                                          @Param("conversationId") Long conversationId,
                                                          @Param("id") Long id);

    /**
     * 按会话级 id 集合批量查询某用户的会话行（软删确认归属 + 会话数统计用）。
     *
     * <p>匹配规则：行 conversation_id IN ids 或 行自身 id IN ids，且强制 user_id 归属校验。
     * <b>注意</b>：必须用显式 JPQL 括号包裹 OR 子句——派生查询名会生成
     * {@code (userId=? AND conversationId IN ?) OR (id IN ?)}，造成按 id 越权，故不采用派生命名。</p>
     *
     * @param userId 住户用户 ID
     * @param ids    会话级 id 集合
     * @return 归属该用户的会话行列表
     */
    @Query("SELECT c FROM AgentConversation c WHERE c.userId = :userId " +
            "AND (c.conversationId IN :ids OR (c.conversationId IS NULL AND c.id IN :ids))")
    List<AgentConversation> findOwnedBySessionIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
