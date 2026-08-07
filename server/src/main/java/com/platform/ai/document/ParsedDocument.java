package com.platform.ai.document;

import java.util.List;

/**
 * 文档解析结果 — 由 {@link DocumentParser} 产出，供切片器消费。
 *
 * @param blocks      解析出的文本块列表
 * @param totalChars  总字符数（用于解析质量/大小判断）
 * @param warnings    解析过程警告（如 OCR 页数超限跳过、无标题样式），非阻断但需记录给管理员
 */
public record ParsedDocument(List<ParsedBlock> blocks, int totalChars, List<String> warnings) {
}
