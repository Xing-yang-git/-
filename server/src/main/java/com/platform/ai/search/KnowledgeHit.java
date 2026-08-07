package com.platform.ai.search;

/**
 * 知识库检索命中结果 — 供 Agent 组装上下文与前端渲染来源引用。
 *
 * @param id           知识条目 ID
 * @param title        条目标题
 * @param content      条目正文（作为 AI 回答依据）
 * @param category     分类：rules/service/help/guide
 * @param source       来源文档名（引用出处展示）
 * @param distance     余弦距离（越小越相似；关键词兜底时为 -1）
 * @param sectionTitle 切片章节标题路径（引用出处增强，可为 null）
 * @param pageNo       切片来源页码（可为 null）
 */
public record KnowledgeHit(Long id, String title, String content, String category, String source, double distance,
                           String sectionTitle, Integer pageNo) {
}
