package com.platform.ai.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识切片器 — 把解析出的文本块切成适合嵌入的切片。
 *
 * <p>策略：整块先过 {@link TextCleaner} 清洗，再按行（分点）贪心累加：
 * 达到目标长度 chunk-size 封卡，硬上限 chunk-max（不拆分整行）；仅当单个分点本身
 * 超过 chunk-max 时才退回滑动窗口（chunk-max + chunk-overlap 重叠）。
 * 无章节标题的块按「方案 B」从每张卡片正文派生独立语义标题，检索可溯源。</p>
 */
@Component
public class DocumentChunker {

    /** 切片目标长度（字符），贪心累加达到后封卡 */
    private final int chunkSize;
    /** 切片硬上限（字符），也是超长单行滑动窗口的窗口大小 */
    private final int chunkMax;
    /** 滑窗重叠字符数 */
    private final int chunkOverlap;
    /** 最短有效切片长度，低于此丢弃 */
    private final int minChunkLength;
    /** 无章节标题时派生标题的长度上限 */
    private static final int DERIVED_TITLE_MAX = 200;

    /**
     * 构造器注入。
     *
     * @param chunkSize      切片目标长度（ai.doc.chunk-size）
     * @param chunkMax       切片硬上限（ai.doc.chunk-max）
     * @param chunkOverlap   滑窗重叠字符数（ai.doc.chunk-overlap）
     * @param minChunkLength 最短有效切片（ai.doc.min-chunk-length）
     */
    public DocumentChunker(@Value("${ai.doc.chunk-size:400}") int chunkSize,
                           @Value("${ai.doc.chunk-max:550}") int chunkMax,
                           @Value("${ai.doc.chunk-overlap:60}") int chunkOverlap,
                           @Value("${ai.doc.min-chunk-length:20}") int minChunkLength) {
        this.chunkSize = chunkSize;
        this.chunkMax = chunkMax;
        this.chunkOverlap = chunkOverlap;
        this.minChunkLength = minChunkLength;
    }

    /**
     * 将解析结果切成切片列表。
     *
     * @param doc             解析结果
     * @param fallbackSection 派生标题为空时的兜底章节名（通常是文档展示来源名）
     * @return 切片列表（已清洗、已过滤过短）
     */
    public List<Chunk> chunk(ParsedDocument doc, String fallbackSection) {
        List<Chunk> result = new ArrayList<>();
        if (doc == null || doc.blocks() == null) {
            return result;
        }
        for (ParsedBlock block : doc.blocks()) {
            String raw = block.text();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            boolean hasRealTitle = block.sectionTitle() != null && !block.sectionTitle().isBlank();
            // 整块先清洗（保留跨行去重能力），再按行拆分
            String cleaned = TextCleaner.clean(raw, null);
            if (cleaned == null || cleaned.length() < minChunkLength) {
                continue;
            }
            String[] lines = cleaned.split("\n");
            StringBuilder current = new StringBuilder();
            for (String line : lines) {
                // 贪心封卡：当前已达目标长度，或再加入下一行会超硬上限
                if (current.length() > 0
                        && (current.length() >= chunkSize || current.length() + line.length() > chunkMax)) {
                    flushCard(result, block, hasRealTitle, fallbackSection, current);
                    current.setLength(0);
                }
                if (line.length() > chunkMax) {
                    // 单个分点超过硬上限 → 滑动窗口切
                    slideChunk(result, block, hasRealTitle, fallbackSection, line);
                } else {
                    if (current.length() > 0) {
                        current.append('\n');
                    }
                    current.append(line);
                }
            }
            flushCard(result, block, hasRealTitle, fallbackSection, current);
        }
        return result;
    }

    /** 超长单行滑动窗口切分（overlap 保留上下文，避免切断关键信息） */
    private void slideChunk(List<Chunk> result, ParsedBlock block, boolean hasRealTitle,
                            String fallbackSection, String line) {
        int step = Math.max(1, chunkMax - chunkOverlap);
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(line.length(), start + chunkMax);
            String piece = line.substring(start, end);
            if (piece.length() >= minChunkLength) {
                result.add(new Chunk(titleOf(hasRealTitle, block.sectionTitle(), piece, fallbackSection),
                        block.pageNo(), piece));
            }
            if (end >= line.length()) {
                break;
            }
            start += step;
        }
    }

    /** 贪心累加的行组封卡；过短丢弃 */
    private void flushCard(List<Chunk> result, ParsedBlock block, boolean hasRealTitle,
                           String fallbackSection, StringBuilder current) {
        String content = current.toString().trim();
        if (content.isEmpty() || content.length() < minChunkLength) {
            return;
        }
        result.add(new Chunk(titleOf(hasRealTitle, block.sectionTitle(), content, fallbackSection),
                block.pageNo(), content));
    }

    /** 有真实标题用标题路径；否则按「方案 B」从卡片正文派生语义标题 */
    private String titleOf(boolean hasRealTitle, String sectionTitle, String content, String fallbackSection) {
        if (hasRealTitle) {
            return sectionTitle;
        }
        String derived = TextTitleDeriver.derive(content, DERIVED_TITLE_MAX);
        return derived != null ? derived : fallbackSection;
    }
}
