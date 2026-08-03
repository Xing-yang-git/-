package com.platform.model.entity;

import com.platform.common.BizStatus;
import com.platform.model.entity.column.KnowledgeItemsColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库条目实体，对应 knowledge_items 表。
 *
 * <p>AI Agent「小邻」的 RAG 检索源，按小区（tenant_id）隔离。
 * embedding 列存 1024 维向量字面量（智谱 embedding-3 dimensions=1024），
 * 生成失败时留空不阻断保存，检索降级关键词 LIKE，可经 reindex 批量补齐。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = KnowledgeItemsColumn.TABLE_NAME)
public class KnowledgeItem {

    /** 知识条目 ID（自增主键） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属小区 ID，外键 → tenants.id */
    @Column(name = KnowledgeItemsColumn.COL_TENANT_ID, nullable = false)
    private Long tenantId;

    /** 分类：rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南)，引用 {@link com.platform.common.KnowledgeCategory} */
    @Column(name = KnowledgeItemsColumn.COL_CATEGORY, nullable = false, length = 20)
    private String category;

    /** 条目标题（如"装修施工时间规定"） */
    @Column(name = KnowledgeItemsColumn.COL_TITLE, nullable = false, length = 200)
    private String title;

    /** 条目正文（检索与问答来源），单条不超过 embedding-3 单次上限 3072 tokens */
    @Column(name = KnowledgeItemsColumn.COL_CONTENT, nullable = false)
    private String content;

    /** 来源文档名（如《小区规章制度》），问答时用于引用出处 */
    @Column(name = KnowledgeItemsColumn.COL_SOURCE, length = 100)
    private String source;

    /** 逗号分隔标签，关键词检索兜底 */
    @Column(name = KnowledgeItemsColumn.COL_TAGS, length = 500)
    private String tags;

    /** 1024 维语义向量字面量 '[0.1,0.2,...]'，失败留空可 reindex 补齐 */
    @Column(name = KnowledgeItemsColumn.COL_EMBEDDING)
    private String embedding;

    /** 状态：online(启用)/offline(停用)，引用 {@link BizStatus} */
    @Column(name = KnowledgeItemsColumn.COL_STATUS, nullable = false, length = 10)
    @Builder.Default
    private String status = BizStatus.ONLINE;

    /** 录入管理员用户 ID → users.id */
    @Column(name = KnowledgeItemsColumn.COL_CREATED_BY)
    private Long createdBy;

    /** 创建时间 */
    @Column(name = KnowledgeItemsColumn.COL_CREATED_AT, nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = KnowledgeItemsColumn.COL_UPDATED_AT, nullable = false)
    private LocalDateTime updatedAt;

    /** 插入前自动填充创建/更新时间 */
    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    /** 更新前自动刷新更新时间 */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
