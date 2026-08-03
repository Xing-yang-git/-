package com.platform.service;

import com.platform.common.BizStatus;
import com.platform.common.KnowledgeCategory;
import com.platform.model.entity.KnowledgeItem;
import com.platform.repository.KnowledgeItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库业务逻辑 — B端管理 CRUD + RAG 向量生成。
 *
 * <p>使用 Spring AI {@link OpenAiEmbeddingModel}（智谱 embedding-3，1024 维）生成向量。
 * embedding 生成失败不阻断保存（检索降级关键词 LIKE），可经 reindex 批量补齐。</p>
 */
@Slf4j
@Service
public class KnowledgeService {

    private final KnowledgeItemRepository knowledgeItemRepository;
    private final OpenAiEmbeddingModel zhipuEmbedding;

    public KnowledgeService(KnowledgeItemRepository knowledgeItemRepository, OpenAiEmbeddingModel zhipuEmbedding) {
        this.knowledgeItemRepository = knowledgeItemRepository;
        this.zhipuEmbedding = zhipuEmbedding;
    }

    /**
     * 管理端分页查询知识条目（含分类/状态/关键词过滤）。
     *
     * @param tenantId 小区 ID
     * @param category 分类过滤（可空）
     * @param status   状态过滤（可空）
     * @param keyword  关键词（可空）
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<KnowledgeItem> list(Long tenantId, String category, String status, String keyword, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return knowledgeItemRepository.findWithFilter(tenantId, category, status, kw, pageable);
    }

    /**
     * 按 ID 查询知识条目（不存在返回 null，供管理端归属校验用）。
     *
     * @param id 条目 ID
     * @return 知识条目实体，或 null
     */
    @Transactional(readOnly = true)
    public KnowledgeItem get(Long id) {
        return knowledgeItemRepository.findById(id).orElse(null);
    }

    /**
     * 创建知识条目（生成 1024 维 embedding，失败留空不阻断）。
     *
     * @param item 待创建条目（含 tenantId、category、title、content）
     * @return 已保存条目
     */
    @Transactional
    public KnowledgeItem create(KnowledgeItem item) {
        validateCategoryAndStatus(item);
        generateEmbedding(item);
        return knowledgeItemRepository.save(item);
    }

    /**
     * 更新知识条目（内容变更后重新生成 embedding）。
     *
     * @param item 待更新条目（必须含 id）
     * @return 已更新条目
     */
    @Transactional
    public KnowledgeItem update(KnowledgeItem item) {
        if (item.getId() == null) {
            throw new IllegalArgumentException("更新知识条目必须携带 id");
        }
        validateCategoryAndStatus(item);
        generateEmbedding(item);
        return knowledgeItemRepository.save(item);
    }

    /**
     * 软上下架知识条目（不物理删除，保留审计）。
     *
     * @param id     条目 ID
     * @param status 目标状态：online(启用)/offline(停用)
     */
    @Transactional
    public void setStatus(Long id, String status) {
        knowledgeItemRepository.findById(id).ifPresent(item -> {
            item.setStatus(status);
            knowledgeItemRepository.save(item);
            log.info("知识条目状态变更: id={}, status={}", id, status);
        });
    }

    /**
     * 批量补齐缺失 embedding（管理端 reindex 按钮调用）。
     *
     * @param tenantId 小区 ID（null 表示全部小区，super_admin 视角）
     * @return 本次补齐的条目数
     */
    @Transactional
    public int reindex(Long tenantId) {
        List<KnowledgeItem> missing = knowledgeItemRepository.findMissing(tenantId);
        int count = 0;
        for (KnowledgeItem item : missing) {
            generateEmbedding(item);
            knowledgeItemRepository.save(item);
            if (item.getEmbedding() != null) {
                count++;
            }
        }
        log.info("知识库向量批量补齐完成: tenantId={}, 本次 {} 条", tenantId, count);
        return count;
    }

    /**
     * 生成 1024 维语义向量并写入条目（失败仅告警，留空待重试）。
     *
     * @param item 目标条目
     */
    private void generateEmbedding(KnowledgeItem item) {
        try {
            String text = ((item.getTitle() == null ? "" : item.getTitle()) + " "
                    + (item.getContent() == null ? "" : item.getContent())).trim();
            if (text.isEmpty()) {
                log.warn("知识条目 [id={}] 标题与正文均为空，跳过向量生成", item.getId());
                return;
            }
            float[] vector = zhipuEmbedding.embed(text);
            item.setEmbedding(floatArrayToPgvectorString(vector));
        } catch (Exception e) {
            log.warn("知识条目 [id={}] 向量生成失败，留空待 reindex: {}", item.getId(), e.getMessage());
        }
    }

    /**
     * 校验知识条目分类与状态取值（创建/更新统一入口，防非法值落库）。
     *
     * @param item 待校验条目
     */
    private void validateCategoryAndStatus(KnowledgeItem item) {
        boolean validCategory = KnowledgeCategory.RULES.equals(item.getCategory())
                || KnowledgeCategory.SERVICE.equals(item.getCategory())
                || KnowledgeCategory.HELP.equals(item.getCategory())
                || KnowledgeCategory.GUIDE.equals(item.getCategory());
        if (!validCategory) {
            throw new IllegalArgumentException("未知分类: " + item.getCategory());
        }
        if (item.getStatus() != null
                && !BizStatus.ONLINE.equals(item.getStatus())
                && !BizStatus.OFFLINE.equals(item.getStatus())) {
            throw new IllegalArgumentException("未知状态: " + item.getStatus());
        }
    }

    /**
     * 将 float[] 转为 pgvector 字面量格式 '[0.1,0.2,...]'。
     *
     * @param vector 浮点向量（必须 1024 维，与 HNSW 表达式索引 vector(1024) 严格一致）
     * @return pgvector 字面量字符串
     * @throws IllegalArgumentException 向量维度非 1024 时抛出（generateEmbedding 捕获后留空）
     */
    static String floatArrayToPgvectorString(float[] vector) {
        if (vector.length != 1024) {
            throw new IllegalArgumentException("知识条目向量维度必须为 1024，实际 " + vector.length);
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
