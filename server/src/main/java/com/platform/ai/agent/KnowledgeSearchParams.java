package com.platform.ai.agent;

/**
 * search_knowledge 工具参数 — 检索小区知识库。
 *
 * @param keyword 检索关键词（必填），由模型提取用户问题核心词后精简改写，不传整句
 */
public record KnowledgeSearchParams(String keyword) {
}
