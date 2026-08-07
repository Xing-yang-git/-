package com.platform.ai.document;

/**
 * 解析出的文本块 — 切片前的最小语义单元。
 *
 * @param sectionTitle 章节标题路径（如 "门禁与访客 / 访客登记"），无标题时可为 null
 * @param headingLevel 标题层级（1~6，0 表示无标题）
 * @param text         块原始文本（清洗在切片阶段由 {@link TextCleaner} 完成）
 * @param pageNo       来源页码（分页文档）；非分页文档为 null
 */
public record ParsedBlock(String sectionTitle, int headingLevel, String text, Integer pageNo) {
}
