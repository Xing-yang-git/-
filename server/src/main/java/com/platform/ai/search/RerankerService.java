package com.platform.ai.search;

import com.platform.ai.common.AiApiInvoker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 知识检索重排服务 — 用本地 rerank-service（FastAPI，兼容 Ollama /api/rerank 契约，部署 bge-reranker-v2-m3）对混合召回候选按与问题相关性重排。
 *
 * <p><b>为何用专用重排器而非 LLM</b>：bge-reranker-v2-m3 是 cross-encoder，专门做相关性排序——
 * 毫秒级、确定性、中文效果好；LLM 零样本重排要么有思维链空 content 问题（deepseek/glm-4.5-air），
 * 要么偶发截断（glm-4-flash），且每次都烧 token。</p>
 *
 * <p>调用本地 rerank-service（FastAPI，兼容 Ollama {@code POST /api/rerank} 契约），按 {@code relevance_score} 降序重排；失败降级原序（不阻断问答）。</p>
 */
@Slf4j
@Service
public class RerankerService {

    private final RestClient rerankRestClient;
    private final AiApiInvoker aiApiInvoker;
    private final String model;

    /**
     * 构造器注入（rerankRestClient 按参数名解析到同名 Bean，避免多 RestClient 类型歧义）。
     *
     * @param rerankRestClient 独立重排服务 RestClient（短超时）
     * @param aiApiInvoker     外部 API 调用封装（重试/熔断）
     * @param model            重排模型名（bge-reranker-v2-m3，服务端忽略）
     */
    public RerankerService(RestClient rerankRestClient,
                           AiApiInvoker aiApiInvoker,
                           @Value("${ai.rerank.model:bge-reranker-v2-m3}") String model) {
        this.rerankRestClient = rerankRestClient;
        this.aiApiInvoker = aiApiInvoker;
        this.model = model;
    }

    /**
     * 重排候选，返回 topM 条（失败降级原序截断）。
     *
     * @param query      用户问题
     * @param candidates 混合召回候选（已去重）
     * @param topM       重排后保留条数
     * @return 重排后的列表（不超过 topM 条）
     */
    public List<KnowledgeHit> rerank(String query, List<KnowledgeHit> candidates, int topM) {
        if (candidates == null || candidates.size() <= 1) {
            return candidates;
        }
        try {
            // 本地服务也可能瞬时不可用：重试 2 次，失败降级原序
            List<KnowledgeHit> ranked = aiApiInvoker.invoke("rerank", 2, () -> doRerank(query, candidates));
            if (ranked.size() > topM) {
                return ranked.subList(0, topM);
            }
            return ranked;
        } catch (Exception e) {
            log.warn("重排降级原序: query 摘要={}, error={}", truncate(query, 30), e.getMessage());
            return candidates.size() > topM ? candidates.subList(0, topM) : candidates;
        }
    }

    /** 调用本地 rerank-service 重排接口，按相关性分数降序返回候选 */
    @SuppressWarnings("unchecked")
    private List<KnowledgeHit> doRerank(String query, List<KnowledgeHit> candidates) {
        List<String> documents = candidates.stream()
                .map(h -> truncate(h.title(), 50) + " | " + truncate(h.content(), 200))
                .toList();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "query", query,
                "documents", documents
        );

        Map<String, Object> response = rerankRestClient.post()
                .uri("/api/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("results")) {
            throw new RuntimeException("重排响应缺少 results 字段");
        }
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("重排响应 results 为空");
        }

        // 按 relevance_score 降序，index 映射回候选
        List<Map<String, Object>> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparingDouble(
                r -> -((Number) r.get("relevance_score")).doubleValue()));
        List<KnowledgeHit> ranked = new ArrayList<>();
        for (Map<String, Object> r : sorted) {
            int idx = ((Number) r.get("index")).intValue();
            if (idx >= 0 && idx < candidates.size()) {
                ranked.add(candidates.get(idx));
            }
        }
        if (ranked.isEmpty()) {
            throw new RuntimeException("重排结果无法映射到候选");
        }
        return ranked;
    }

    /** 截断文本 */
    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
