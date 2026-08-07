package com.platform.ai.document;

/**
 * 知识切片 — 导入 knowledge_items 的最小单元（含章节/页码溯源）。
 *
 * @param sectionTitle 切片标题：有章节标题时为完整路径（可含上级标题）；
 *                     无章节标题时为从正文派生的语义标题（≤ 200 字）
 * @param pageNo       来源页码（分页文档）；非分页文档为 null
 * @param content      清洗后的切片正文
 */
public record Chunk(String sectionTitle, Integer pageNo, String content) {
}
