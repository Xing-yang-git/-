package com.platform.ai.document;

import java.util.regex.Pattern;

/**
 * 切片标题派生 — 无章节标题时按整句切取正文开头作标题，替代盲截前 N 字。
 *
 * <p>句界符：中文句号/问号/感叹号/分号 ＋ 英文句点/叹号/问号/分号。贪心取整句累加，
 * 直到再加入下一句会超上限；首句即超限时硬截该句前部，保证标题永不为空。</p>
 */
public final class TextTitleDeriver {

    /** 句界符（lookbehind 使句界符保留在句尾）：中文 。！？； ＋ 英文 .!?; */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？；.!?;])");

    /** 工具类，禁止实例化 */
    private TextTitleDeriver() {
    }

    /**
     * 从文本开头按整句派生标题，累计不超过 maxLength。
     *
     * @param text      源文本（通常为切片正文）
     * @param maxLength 标题长度上限（字符）
     * @return 派生标题；text 为空白时返回 null
     */
    public static String derive(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return null;
        }
        StringBuilder title = new StringBuilder();
        for (String sentence : SENTENCE_BOUNDARY.split(text)) {
            String part = sentence.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (title.length() + part.length() > maxLength) {
                if (title.length() == 0) {
                    // 首句即超限 → 硬截该句前部
                    return part.substring(0, maxLength);
                }
                break;
            }
            title.append(part);
        }
        return title.toString().trim();
    }
}
