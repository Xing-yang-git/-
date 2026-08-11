package com.platform.repository;

import com.platform.model.entity.KnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识库条目数据访问层 — 管理端 CRUD + RAG 检索支持。
 */
public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

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
     * 查系统内置平台帮助条目（docId 为空 = 非文档切片，由 DataInitializer 播种）——
     * 供 AgentPromptBuilder 注入 System Prompt 作为平台功能权威说明（注册认证/发布/借入/求助/AI 审核规则）。
     *
     * @param category 分类（平台帮助为 {@code help}）
     * @return 内置平台帮助条目列表（按 id 升序，保证注入顺序稳定）
     */
    @Query("SELECT k FROM KnowledgeItem k WHERE k.docId IS NULL AND k.category = :category ORDER BY k.id")
    List<KnowledgeItem> findBuiltinByCategory(@Param("category") String category);

    /**
     * 删除某文档的全部切片（文档重试/替换前幂等清理；删除文档行时由 DB 外键 CASCADE 兜底）。
     *
     * @param docId 来源文档 ID
     */
    @Modifying
    @Query("DELETE FROM KnowledgeItem k WHERE k.docId = :docId")
    void deleteByDocId(@Param("docId") Long docId);
}
