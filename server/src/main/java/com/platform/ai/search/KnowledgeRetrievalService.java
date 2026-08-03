package com.platform.ai.search;

import com.platform.common.BizStatus;
import com.platform.repository.KnowledgeItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库 RAG 检索服务 — AI Agent「小邻」回答前从知识库检索相关资料。
 *
 * <p>检索策略（两级降级）：
 * <ol>
 *   <li>查询文本 → 智谱 embedding-3（1024 维）→ pgvector 余弦距离，命中 HNSW 表达式索引</li>
 *   <li>向量结果为空 / 向量生成失败 → 关键词 LIKE（标题/正文/标签）兜底</li>
 * </ol>
 *
 * <p>返回的 {@link KnowledgeHit} 由后端直接生成 sources（非模型输出），防幻觉引用。</p>
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {

    private final EntityManager entityManager;
    private final OpenAiEmbeddingModel zhipuEmbedding;
    private final KnowledgeItemRepository knowledgeItemRepository;

    /** 检索返回条数上限 */
    @Value("${ai.agent.knowledge-top-k:5}")
    private int topK;

    /** 余弦距离阈值（小于此值才视为语义相关） */
    @Value("${ai.agent.knowledge-threshold:0.25}")
    private double threshold;

    public KnowledgeRetrievalService(EntityManager entityManager,
                                     OpenAiEmbeddingModel zhipuEmbedding,
                                     KnowledgeItemRepository knowledgeItemRepository) {
        this.entityManager = entityManager;
        this.zhipuEmbedding = zhipuEmbedding;
        this.knowledgeItemRepository = knowledgeItemRepository;
    }

    /**
     * 检索与查询相关的知识条目（向量优先，关键词兜底）。
     *
     * @param tenantId  当前用户所在小区 ID
     * @param queryText 用户提问文本
     * @return 按相关度排序的知识命中列表，可能为空
     */
    public List<KnowledgeHit> search(Long tenantId, String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return List.of();
        }
        String emb = embedQuery(queryText);
        List<KnowledgeHit> hits = new ArrayList<>();
        if (emb != null && tenantId != null) {
            hits = vectorSearch(tenantId, emb);
        }
        if (hits.isEmpty()) {
            hits = keywordSearch(tenantId, queryText);
        }
        return hits;
    }

    /**
     * 将查询文本编码为 1024 维向量字符串（失败返回 null，触发关键词兜底）。
     *
     * @param text 查询文本
     * @return pgvector 字面量，或 null
     */
    private String embedQuery(String text) {
        try {
            float[] vector = zhipuEmbedding.embed(text);
            if (vector.length != 1024) {
                log.warn("查询向量维度异常: {}，降级关键词检索", vector.length);
                return null;
            }
            return toPgvectorString(vector);
        } catch (Exception e) {
            log.warn("查询向量生成失败，降级关键词检索: {}", e.getMessage());
            return null;
        }
    }

    /**
     * pgvector 余弦距离检索（命中 HNSW 表达式索引 vector(1024)）。
     *
     * @param tenantId 小区 ID
     * @param emb      查询向量字面量
     * @return 语义相关命中列表
     */
    @SuppressWarnings("unchecked")
    private List<KnowledgeHit> vectorSearch(Long tenantId, String emb) {
        String sql = "SELECT k.id, k.title, k.content, k.category, k.source, " +
                "CAST(k.embedding AS vector(1024)) <=> CAST(:emb AS vector(1024)) AS distance " +
                "FROM knowledge_items k " +
                "WHERE k.tenant_id = :tenantId AND k.status = :status AND k.embedding IS NOT NULL " +
                "AND CAST(k.embedding AS vector(1024)) <=> CAST(:emb AS vector(1024)) < :threshold " +
                "ORDER BY distance ASC LIMIT :limit";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("tenantId", tenantId);
        query.setParameter("status", BizStatus.ONLINE);
        query.setParameter("emb", emb);
        query.setParameter("threshold", threshold);
        query.setParameter("limit", topK);
        List<Object[]> rows = query.getResultList();
        List<KnowledgeHit> hits = rows.stream()
                .map(r -> new KnowledgeHit(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        (String) r[2],
                        (String) r[3],
                        (String) r[4],
                        ((Number) r[5]).doubleValue()))
                .collect(Collectors.toList());
        log.debug("知识库向量检索: query 命中 {} 条", hits.size());
        return hits;
    }

    /**
     * 关键词 LIKE 兜底检索（标题/正文/标签）。
     *
     * @param tenantId 小区 ID
     * @param keyword  关键词
     * @return 关键词命中列表（distance 置为 -1 表示非语义排序）
     */
    private List<KnowledgeHit> keywordSearch(Long tenantId, String keyword) {
        if (tenantId == null) {
            return List.of();
        }
        return knowledgeItemRepository
                .findWithFilter(tenantId, null, BizStatus.ONLINE, keyword, PageRequest.of(0, topK))
                .getContent()
                .stream()
                .map(k -> new KnowledgeHit(k.getId(), k.getTitle(), k.getContent(),
                        k.getCategory(), k.getSource(), -1.0))
                .collect(Collectors.toList());
    }

    /**
     * 将 1024 维 float[] 转为 pgvector 字面量 '[0.1,0.2,...]'。
     *
     * @param vector 查询向量
     * @return pgvector 字面量字符串
     */
    private String toPgvectorString(float[] vector) {
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
