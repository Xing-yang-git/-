package com.platform.ai.agent;

import com.platform.ai.common.PromptRepository;
import com.platform.model.entity.AgentMemorySegment;
import com.platform.repository.AgentMemorySegmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆检索服务 — 新窗口开始时把用户历史长期记忆（压缩段）整合为 {@code {历史记忆}} 变量。
 *
 * <p>链路：向量化 queryText → 按用户过滤的向量检索 top-N（阈值过滤在服务层）→ 无命中返回「无」→
 * 读 {@code memory.memory-summary} 渲染命中摘要列表 → LLM 整合为一段记忆变量。
 * <b>降级铁律</b>：embedding 失败 / LLM 失败 → 返回 null 不阻塞对话（注入侧将 null 渲染为「无」）。</p>
 */
@Slf4j
@Service
public class MemoryRetrievalService {

    private final PromptRepository promptRepository;
    private final OpenAiChatModel deepseekChatModel;
    private final OpenAiEmbeddingModel zhipuEmbedding;
    private final AgentMemorySegmentRepository memorySegmentRepository;

    /** 记忆召回条数（检索该用户压缩段 top-N） */
    @Value("${ai.agent.memory-recall-top:3}")
    private int memoryRecallTop;

    /** 记忆匹配距离阈值（余弦距离，≤ 此值的摘要才注入；低于阈值的摘要不注入） */
    @Value("${ai.agent.memory-match-threshold:0.45}")
    private double memoryMatchThreshold;

    /** 记忆整合 LLM 温度（与压缩一致的精准档） */
    private static final double SUMMARY_TEMPERATURE = 0.2;

    /** 记忆整合 LLM 最大输出 token */
    private static final int SUMMARY_MAX_TOKENS = 1024;

    /** 无命中时的记忆变量文本 */
    private static final String MEMORY_NONE = "无";

    /**
     * 构造器注入。
     *
     * @param promptRepository        提示词仓库（key {@code memory.memory-summary}）
     * @param deepseekChatModel       DeepSeek 对话模型（与 AgentService 同一 Bean）
     * @param zhipuEmbedding          智谱 embedding 模型（与知识库同款 1024 维，写入 vector(1024) 压缩段）
     * @param memorySegmentRepository 压缩段仓储（按用户检索）
     */
    public MemoryRetrievalService(PromptRepository promptRepository,
                                  OpenAiChatModel deepseekChatModel,
                                  OpenAiEmbeddingModel zhipuEmbedding,
                                  AgentMemorySegmentRepository memorySegmentRepository) {
        this.promptRepository = promptRepository;
        this.deepseekChatModel = deepseekChatModel;
        this.zhipuEmbedding = zhipuEmbedding;
        this.memorySegmentRepository = memorySegmentRepository;
    }

    /**
     * 加载用户长期记忆上下文变量（供 System Prompt 的 {@code {历史记忆}} 注入）。
     *
     * @param userId    住户用户 ID（检索强制按用户过滤，越权防护）
     * @param queryText 当前窗口首条用户消息（作检索查询文本）
     * @return 整合后的记忆变量文本；无命中返回「无」；embedding/LLM 失败降级返回 null（不阻塞）
     */
    public String loadMemoryContext(Long userId, String queryText) {
        if (queryText == null || queryText.isBlank()) {
            return null;
        }
        // 1. 向量化查询文本（失败降级返回 null，不阻塞对话）
        String queryVector = embedQuery(queryText);
        if (queryVector == null) {
            return null;
        }
        // 2. 按用户检索 top-N，阈值过滤在服务层（与知识库检索同款做法）
        List<AgentMemorySegment> hits;
        try {
            hits = searchWithThreshold(userId, queryVector);
        } catch (Exception e) {
            // 检索异常（如 pgvector 维度不匹配导致查询失败）→ 降级视作无记忆，绝不抛到 chat 请求
            log.warn("记忆检索异常（降级不注入记忆）: userId={}, {}", userId, e.getMessage());
            return null;
        }
        // 3. 无命中 → 返回「无」
        if (hits.isEmpty()) {
            return MEMORY_NONE;
        }
        // 4. 渲染 memory-summary 提示词 → LLM 整合（失败降级返回 null）
        String segmentsText = buildSegmentsText(hits);
        String prompt = promptRepository.get("memory.memory-summary").replace("{segments}", segmentsText);
        try {
            Message system = new SystemMessage(prompt);
            ChatResponse response = deepseekChatModel.call(new Prompt(List.of(system),
                    OpenAiChatOptions.builder()
                            .temperature(SUMMARY_TEMPERATURE)
                            .maxTokens(SUMMARY_MAX_TOKENS)
                            .build()));
            String summary = response.getResult().getOutput().getText();
            if (summary == null || summary.isBlank()) {
                log.warn("记忆整合 LLM 回复为空（降级不注入）: userId={}", userId);
                return null;
            }
            log.info("记忆检索注入: userId={}, 命中 {} 段", userId, hits.size());
            return summary.trim();
        } catch (Exception e) {
            log.warn("记忆整合 LLM 失败（降级不注入）: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 向量化查询文本（失败返回 null，跳过记忆检索）。
     *
     * <p>与知识库同款：使用 {@code zhipuEmbedding}（智谱 embedding-3，dimensions=1024），
     * 与 agent_memory_segments.embedding 的 vector(1024) 严格一致。</p>
     *
     * @param text 查询文本
     * @return pgvector 字面量，或 null
     */
    private String embedQuery(String text) {
        try {
            float[] vector = zhipuEmbedding.embed(text);
            if (vector.length != 1024) {
                log.warn("记忆查询向量维度异常: {}，跳过记忆检索", vector.length);
                return null;
            }
            return toPgvectorString(vector);
        } catch (Exception e) {
            log.warn("记忆检索查询向量化失败（降级不注入记忆）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 float[] 转为 pgvector 字面量格式 '[0.1,0.2,...]'。
     *
     * @param vector 浮点向量（1024 维，与 agent_memory_segments.embedding 的 vector(1024) 严格一致）
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

    /**
     * 按用户向量检索压缩段并按距离阈值过滤（保持距离升序）。
     *
     * @param userId      住户用户 ID
     * @param queryVector 查询向量字面量
     * @return 距离 ≤ 阈值的压缩段列表（升序）
     */
    private List<AgentMemorySegment> searchWithThreshold(Long userId, String queryVector) {
        List<Object[]> rows = memorySegmentRepository.findIdsBySimilarity(userId, queryVector, memoryRecallTop);
        // 距离 ≤ 阈值的候选 id（距离越小越相似；超过阈值的不注入）
        List<Long> hitIds = new ArrayList<>();
        for (Object[] row : rows) {
            double distance = ((Number) row[1]).doubleValue();
            if (distance <= memoryMatchThreshold) {
                hitIds.add(((Number) row[0]).longValue());
            }
        }
        if (hitIds.isEmpty()) {
            return List.of();
        }
        // 按距离升序回填实体（findAllById 无序，需按 hitIds 顺序还原）
        Map<Long, AgentMemorySegment> byId = new LinkedHashMap<>();
        for (AgentMemorySegment segment : memorySegmentRepository.findAllById(hitIds)) {
            byId.put(segment.getId(), segment);
        }
        List<AgentMemorySegment> ordered = new ArrayList<>();
        for (Long id : hitIds) {
            AgentMemorySegment segment = byId.get(id);
            if (segment != null) {
                ordered.add(segment);
            }
        }
        return ordered;
    }

    /**
     * 构建命中摘要列表文本（带序号，供 memory-summary 提示词渲染）。
     *
     * @param segments 命中的压缩段（按距离升序）
     * @return 形如「1. 摘要1\n2. 摘要2」的文本
     */
    private String buildSegmentsText(List<AgentMemorySegment> segments) {
        StringBuilder sb = new StringBuilder();
        int seq = 1;
        for (AgentMemorySegment segment : segments) {
            String summary = segment.getSummary();
            if (summary != null && !summary.isBlank()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(seq).append(". ").append(summary.trim());
                seq++;
            }
        }
        return sb.toString();
    }
}
