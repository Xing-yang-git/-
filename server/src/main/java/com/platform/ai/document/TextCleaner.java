package com.platform.ai.document;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本清洗 — 切片入向量前的净化：空白归一、去页码/页眉重复行、去纯日期行（落款日期）、
 * 去样板垃圾文本、相邻重复去重。
 *
 * <p>跨文档/跨页的页眉页脚统计去重属 v2 增强；v1 聚焦单块内的确定性清洗。</p>
 */
public final class TextCleaner {

    /** 工具类，禁止实例化 */
    private TextCleaner() {
    }

    /** 独立成行的页码（如 "3"、"第 3 页"、"Page 3"） */
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile("^\\s*(第?\\s*\\d+\\s*页|Page\\s*\\d+|[-\\d]+)\\s*$");
    /** 独立成行的纯日期（落款/页脚日期噪音）：2026年8月4日、二〇二六年八月四日、2026-08-04、8月4日 */
    private static final Pattern DATE_ONLY_LINE = Pattern.compile(
            "^[0-9〇一二三四五六七八九十零百千]+年[0-9〇一二三四五六七八九十零]+月[0-9〇一二三四五六七八九十零]+日$|" +
                    "^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}$|" +
                    "^[0-9〇一二三四五六七八九十零]+月[0-9〇一二三四五六七八九十零]+日$");
    /** 三个及以上连续空白（含换行）压缩为两个换行 */
    private static final Pattern MULTI_BLANK = Pattern.compile("\\n{3,}");
    /** 行内两个及以上连续空格压缩为单个 */
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]{2,}");

    /**
     * 清洗单个文本块。
     *
     * @param raw         原始文本
     * @param boilerplate 需剔除的样板垃圾文本（页眉/页脚/水印/版权等，逐条整段 remove）；可为 null
     * @return 清洗后的文本；清洗后为空返回 null
     */
    public static String clean(String raw, List<String> boilerplate) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (boilerplate != null) {
            for (String bp : boilerplate) {
                if (bp != null && !bp.isBlank()) {
                    text = text.replace(bp, "");
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        String prevLine = null;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (PAGE_NUMBER_LINE.matcher(trimmed).matches()) {
                continue;
            }
            if (DATE_ONLY_LINE.matcher(trimmed).matches()) {
                // 纯日期行（落款/页脚日期）剔除
                continue;
            }
            if (trimmed.equals(prevLine)) {
                // 相邻重复行（如每页重复的页眉/页脚一行）去重
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(MULTI_SPACE.matcher(trimmed).replaceAll(" "));
            prevLine = trimmed;
        }
        String cleaned = MULTI_BLANK.matcher(sb.toString().trim()).replaceAll("\n\n");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
