package com.platform.repository;

import com.platform.model.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识库条目数据访问层 — 管理端 CRUD + RAG 检索支持。
 */
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

    /**
     * 按小区 + 状态查询启用条目（Agent 检索用）。
     *
     * @param tenantId 小区 ID
     * @param status   状态（online/offline）
     * @return 启用条目列表
     */
    List<KnowledgeItem> findByTenantIdAndStatus(Long tenantId, String status);

    /**
     * 管理端列表 — 支持分类/状态/关键词组合过滤。
     *
     * @param tenantId 小区 ID
     * @param category 分类（可空）
     * @param status   状态（可空）
     * @param kw       关键词（标题/正文/标签 LIKE，可空）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE (:tenantId IS NULL OR k.tenantId = :tenantId) " +
            "AND (:category IS NULL OR k.category = :category) " +
            "AND (:status IS NULL OR k.status = :status) " +
            "AND (:kw IS NULL OR k.title LIKE %:kw% OR k.content LIKE %:kw% OR k.tags LIKE %:kw%)")
    Page<KnowledgeItem> findWithFilter(@Param("tenantId") Long tenantId,
                                       @Param("category") String category,
                                       @Param("status") String status,
                                       @Param("kw") String kw,
                                       Pageable pageable);

    /**
     * 查找缺失向量的条目（批量补齐用），支持按小区过滤。
     *
     * @param tenantId 小区 ID（null 表示全部小区）
     * @return embedding 为空的条目列表
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.embedding IS NULL AND (:tenantId IS NULL OR k.tenantId = :tenantId)")
    List<KnowledgeItem> findMissing(@Param("tenantId") Long tenantId);
}
