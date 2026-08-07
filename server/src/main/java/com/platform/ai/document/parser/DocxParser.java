package com.platform.ai.document.parser;

import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.common.KnowledgeFileType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Word 解析器（docx）— 按「标题1~6」样式段落切块并维护标题栈（完整路径，同 Markdown/PDF），
 * 正文/表格归入当前章节。
 *
 * <p>无任何标题样式的文档退化为单块，并记录 warning（切片丢失章节语义）。</p>
 */
@Component
public class DocxParser implements DocumentParser {

    /** 章节标题路径上限（与 knowledge_items.section_title VARCHAR(200) 对齐） */
    private static final int MAX_SECTION_PATH = 200;

    @Override
    public String supportedType() {
        return KnowledgeFileType.DOCX;
    }

    @Override
    public ParsedDocument parse(Path filePath) throws Exception {
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(filePath))) {
            List<ParsedBlock> blocks = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            boolean foundHeading = false;
            Deque<ParsedHeading> stack = new ArrayDeque<>();
            StringBuilder current = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String style = paragraph.getStyle();
                    if (style != null && isHeadingStyle(style)) {
                        foundHeading = true;
                        flush(blocks, stack, current);
                        pushHeading(stack, text.trim(), headingLevelOf(style));
                    } else {
                        appendLine(current, text.trim());
                    }
                } else if (element instanceof XWPFTable table) {
                    appendLine(current, tableToText(table));
                }
            }
            flush(blocks, stack, current);
            if (!foundHeading) {
                warnings.add("文档无标题样式，切片丢失章节语义，建议使用 Word 标题样式重新排版");
            }
            int totalChars = blocks.stream().mapToInt(b -> b.text().length()).sum();
            return new ParsedDocument(blocks, totalChars, warnings);
        }
    }

    /** 是否标题样式：Heading1~6 或 Title（Title 作为文档大标题的 0 级根） */
    private static boolean isHeadingStyle(String style) {
        String s = style.toLowerCase();
        return s.equals("title") || s.startsWith("heading");
    }

    /** 从 "Heading3" 提取层级 3；Title 为 0 级根；非数字兜底 1 */
    private int headingLevelOf(String style) {
        if (style.equalsIgnoreCase("title")) {
            return 0;
        }
        String digits = style.replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            return Integer.parseInt(digits);
        }
        return 1;
    }

    /** 标题入栈：弹出所有层级 ≥ 当前层级的旧标题，再压入新标题 */
    private static void pushHeading(Deque<ParsedHeading> stack, String title, int level) {
        while (!stack.isEmpty() && stack.peekLast().level() >= level) {
            stack.removeLast();
        }
        stack.addLast(new ParsedHeading(level, title));
    }

    /** 标题栈拼接完整路径（父级在前），超长截断 */
    private static String pathOf(Deque<ParsedHeading> stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String path = stack.stream().map(ParsedHeading::title).collect(Collectors.joining(" / "));
        return path.length() > MAX_SECTION_PATH ? path.substring(0, MAX_SECTION_PATH) : path;
    }

    /** 向当前块追加一行 */
    private static void appendLine(StringBuilder current, String line) {
        if (current.length() > 0) {
            current.append('\n');
        }
        current.append(line);
    }

    /** 表格逐行转文本（单元格以 | 分隔） */
    private static String tableToText(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = row.getTableCells().stream()
                    .map(XWPFTableCell::getText)
                    .collect(Collectors.toList());
            sb.append(String.join(" | ", cells)).append('\n');
        }
        return sb.toString().trim();
    }

    /** 累积的正文作为一个块落盘并清空；标题取当前栈路径与叶子层级 */
    private static void flush(List<ParsedBlock> blocks, Deque<ParsedHeading> stack, StringBuilder current) {
        String content = current.toString().trim();
        if (!content.isEmpty()) {
            blocks.add(new ParsedBlock(pathOf(stack),
                    stack.isEmpty() ? 0 : stack.peekLast().level(), content, null));
        }
        current.setLength(0);
    }

    /** 标题栈条目：层级 + 标题文本 */
    private record ParsedHeading(int level, String title) {
    }
}
