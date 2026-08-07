package com.platform.ai.document.parser;

import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.ai.document.TextCharsetDetector;
import com.platform.common.KnowledgeFileType;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown 解析器 — 按标题层级（# ~ ######）切出叶子段，sectionTitle 保留标题路径。
 *
 * <p>对正文做 markdown 脏标签清洗（图片/链接/强调/列表标记），仅保留纯文本用于向量。
 * 标题栈维护 1~6 级嵌套路径；导出 PDF 再转回 md 的残留分页符（纯符号行）仅在未夹在
 * 正文之间时删除，业务分割线原样保留。</p>
 */
@Component
public class MarkdownParser implements DocumentParser {

    /** 标题行：1~6 个 # + 空格 + 标题文本 */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    /** 纯符号行：整行只含 - * _ 与空格（导出 PDF 转回 md 的分页符/分割线残留） */
    private static final Pattern SYMBOL_LINE = Pattern.compile("^[-*_\\s]+$");
    /** 章节标题路径上限（与 knowledge_items.section_title VARCHAR(200) 对齐） */
    private static final int MAX_SECTION_PATH = 200;

    /** 行类型：空行 / 标题 / 纯符号行 / 正文 */
    private enum LineKind {
        /** 空行 */
        EMPTY,
        /** 标题行（text 为已清洗的标题文本） */
        HEADING,
        /** 纯符号行（分页符/分割线残留） */
        SYMBOL,
        /** 正文行 */
        BODY
    }

    /** 单行标注：kind + 标题层级 + 文本 */
    private record MarkdownLine(LineKind kind, int headingLevel, String text) {
    }

    /**
     * 清洗一行 markdown 标记。
     *
     * @param s 原始行
     * @return 纯文本
     */
    private static String stripMarkdown(String s) {
        String t = s;
        t = t.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");                 // 图片 ![alt](url)
        t = t.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");              // 链接 [text](url) → text
        t = t.replaceAll("[`*_~>]", "");                                    // 强调/代码/引用标记
        t = t.replaceAll("^[-+*]\\s+|^\\d+\\.\\s+", "");                    // 列表标记
        return t.trim();
    }

    /** 判断是否为纯符号行（至少 3 个 - / * / _，如 ---、***、- - -） */
    private static boolean isPureSymbolLine(String line) {
        if (!SYMBOL_LINE.matcher(line).matches()) {
            return false;
        }
        long symbolCount = line.chars().filter(c -> c == '-' || c == '*' || c == '_').count();
        return symbolCount >= 3;
    }

    @Override
    public String supportedType() {
        return KnowledgeFileType.MD;
    }

    @Override
    public ParsedDocument parse(Path filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(filePath);
        Charset charset = TextCharsetDetector.detect(bytes);
        String[] rawLines = new String(bytes, charset).split("\n");
        List<MarkdownLine> lines = classifyLines(rawLines);
        List<ParsedBlock> blocks = new ArrayList<>();
        // 标题栈：addLast/removeLast 视作栈顶，stream() 从头到尾即父级到叶子
        Deque<MarkdownLine> stack = new ArrayDeque<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            MarkdownLine line = lines.get(i);
            switch (line.kind()) {
                case EMPTY -> {
                    // 跳过；仅作为符号行判定的邻居边界
                }
                case HEADING -> {
                    flush(blocks, stack, current);
                    pushHeading(stack, line.headingLevel(), line.text());
                }
                case SYMBOL -> {
                    // 前后都是正文 → 业务分割线，保留；否则视为分页残留删除
                    if (hasBodyNeighbors(lines, i)) {
                        if (current.length() > 0) {
                            current.append('\n');
                        }
                        current.append(line.text());
                    }
                }
                case BODY -> {
                    String content = stripMarkdown(line.text());
                    if (!content.isEmpty()) {
                        if (current.length() > 0) {
                            current.append('\n');
                        }
                        current.append(content);
                    }
                }
            }
        }
        flush(blocks, stack, current);
        int totalChars = blocks.stream().mapToInt(b -> b.text().length()).sum();
        return new ParsedDocument(blocks, totalChars, List.of());
    }

    /** 第一遍扫描：对每行贴标签（标题文本即做清洗，符号行在剥标记前识别） */
    private static List<MarkdownLine> classifyLines(String[] rawLines) {
        List<MarkdownLine> lines = new ArrayList<>();
        for (String rawLine : rawLines) {
            String line = rawLine.replace("\r", "").trim();
            if (line.isEmpty()) {
                lines.add(new MarkdownLine(LineKind.EMPTY, 0, ""));
                continue;
            }
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches()) {
                lines.add(new MarkdownLine(LineKind.HEADING, matcher.group(1).length(),
                        stripMarkdown(matcher.group(2))));
                continue;
            }
            if (isPureSymbolLine(line)) {
                lines.add(new MarkdownLine(LineKind.SYMBOL, 0, line));
                continue;
            }
            lines.add(new MarkdownLine(LineKind.BODY, 0, line));
        }
        return lines;
    }

    /** 标题入栈：弹出所有层级 ≥ 当前层级的旧标题，再压入新标题 */
    private static void pushHeading(Deque<MarkdownLine> stack, int level, String title) {
        while (!stack.isEmpty() && stack.peekLast().headingLevel() >= level) {
            stack.removeLast();
        }
        stack.addLast(new MarkdownLine(LineKind.HEADING, level, title));
    }

    /** 累积的正文作为一个块落盘并清空；sectionTitle 取标题栈完整路径 */
    private static void flush(List<ParsedBlock> blocks, Deque<MarkdownLine> stack, StringBuilder current) {
        String content = current.toString().trim();
        if (!content.isEmpty()) {
            blocks.add(new ParsedBlock(buildPath(stack),
                    stack.isEmpty() ? 0 : stack.peekLast().headingLevel(), content, null));
        }
        current.setLength(0);
    }

    /** 标题栈拼接完整路径（父级在前），超长截断 */
    private static String buildPath(Deque<MarkdownLine> stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String path = stack.stream().map(MarkdownLine::text).collect(Collectors.joining(" / "));
        return path.length() > MAX_SECTION_PATH ? path.substring(0, MAX_SECTION_PATH) : path;
    }

    /** 纯符号行是否夹在正文之间（决定保留/删除） */
    private static boolean hasBodyNeighbors(List<MarkdownLine> lines, int index) {
        return isBodyAfterSkippingBlanks(lines, index - 1, -1)
                && isBodyAfterSkippingBlanks(lines, index + 1, 1);
    }

    /** 从 start 起沿 step 方向跳过空行，遇到的首个非空行是否为正文 */
    private static boolean isBodyAfterSkippingBlanks(List<MarkdownLine> lines, int start, int step) {
        for (int i = start; i >= 0 && i < lines.size(); i += step) {
            if (lines.get(i).kind() == LineKind.EMPTY) {
                continue;
            }
            return lines.get(i).kind() == LineKind.BODY;
        }
        return false;
    }
}
