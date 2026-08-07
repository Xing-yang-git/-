package com.platform.ai.document.parser;

import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.ai.document.ocr.VisionOcrClient;
import com.platform.common.KnowledgeFileType;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PDF 解析器 — 文本页用 PDFTextStripper 抽取，扫描页渲染成图片走 GLM-4V OCR。
 *
 * <p>两遍流程：第一遍本地抽取全部页文本、按有效可见字符判定文本/扫描页并统计扫描页数，
 * 超限直接失败（零 OCR 调用）；随后做页眉页脚清洗（跨页重复行 + "第N页"页脚），
 * 未超限才第二遍逐页解析。
 * 页内按标题启发式（编号/括号形态）识别标题并切块，标题栈跨页继承层级路径（同 Markdown）；
 * 文档首行裸文字标题提升为 0 级根标题。OCR 异常 fail-fast 整篇失败；OCR 空结果视为空白页跳过。</p>
 */
@Slf4j
@Component
public class PdfParser implements DocumentParser {

    private final VisionOcrClient visionOcrClient;
    /** 单页有效可见字符数低于此值判定为扫描页 */
    private final int scanThreshold;
    /** PDF 渲染 OCR 分辨率（DPI） */
    private final float ocrDpi;
    /** 扫描版 OCR 页数上限，超限直接拒绝入库 */
    private final int maxOcrPages;
    /** 单页渲染 JPEG 大小上限（字节），超限自动降 DPI 重试一次，防 GLM-4V 单图过大 */
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    /** 标题行最大长度（字符），超过视为正文 */
    private static final int MAX_HEADING_LEN = 40;
    /** 章节标题路径上限（与 knowledge_items.section_title VARCHAR(200) 对齐） */
    private static final int MAX_SECTION_PATH = 200;
    /** 句尾标点（含句号等结尾的行视为正文而非标题） */
    private static final Pattern ENDS_WITH_SENTENCE = Pattern.compile(".*[。！？；.!?;]$");
    /** 纯页码行（"3" / "第 3 页"） */
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile("^第?\\s*\\d+\\s*页?$|^[-\\d]+$");
    /** 纯符号行（分页符/分割线残留） */
    private static final Pattern SYMBOL_LINE = Pattern.compile("^[-*_\\s]+$");
    /**
     * 页脚行：标题前缀 + 页码（第N页/第N页共M页/Page N/Page N of M），或纯数字页码（3/20、- 3 -）。
     * 日期类（2024/1/3）因含两个斜杠不匹配，避免误删正文。
     */
    private static final Pattern FOOTER_PAGE_LINE = Pattern.compile(
            "^(?:.{0,30}(?:第\\s*\\d+\\s*页(?:\\s*共\\s*\\d+\\s*页)?|Page\\s*\\d+(?:\\s*of\\s*\\d+)?)|" +
                    "\\d+\\s*/\\s*\\d+|-\\s*\\d+\\s*-)$");
    /** 标题形态：第X章 / 中文序数 / 括号子标题 / 括号序号 / 多级数字 */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(第[一二三四五六七八九十百零\\d]+[章节条部分]|" +   // 第3章 / 第二章
                    "[一二三四五六七八九十]+[、．.]|" +             // 一、注册与登录
                    "[【「][^】」]+[】」]|" +                      // 【办理条件】/「申请材料」
                    "[（(][一二三四五六七八九十\\d]+[）)]|" +      // （一）/（1）
                    "\\d+\\.\\d+(\\.\\d+)*[、．.]?)");            // 1.1 / 1.1.1（裸 1. 易与分点混淆，不判标题）
    /** 多级数字前缀（用于层级推断：1.1 → 3 级，1.1.1 → 4 级） */
    private static final Pattern DECIMAL_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+)*)");

    /**
     * 构造器注入。
     *
     * @param visionOcrClient GLM-4V OCR 客户端
     * @param scanThreshold   扫描页判定阈值
     * @param ocrDpi          渲染 DPI
     * @param maxOcrPages     OCR 页数上限
     */
    public PdfParser(VisionOcrClient visionOcrClient,
                     @Value("${ai.doc.ocr-scan-threshold:20}") int scanThreshold,
                     @Value("${ai.doc.ocr-dpi:150}") float ocrDpi,
                     @Value("${ai.doc.ocr-max-pages:20}") int maxOcrPages) {
        this.visionOcrClient = visionOcrClient;
        this.scanThreshold = scanThreshold;
        this.ocrDpi = ocrDpi;
        this.maxOcrPages = maxOcrPages;
    }

    @Override
    public String supportedType() {
        return KnowledgeFileType.PDF;
    }

    @Override
    public ParsedDocument parse(Path filePath) throws Exception {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("文件加密，请解除密码后重新上传");
            }
            int totalPages = document.getNumberOfPages();
            PDDocumentInformation info = document.getDocumentInformation();
            boolean hasDocTitle = info != null && info.getTitle() != null && !info.getTitle().isBlank();

            // 第一遍：本地抽取全部页文本，判定文本/扫描页，统计扫描页数（零 API）
            String[] pageTexts = new String[totalPages];
            boolean[] isScanned = new boolean[totalPages];
            int scannedCount = 0;
            for (int page = 0; page < totalPages; page++) {
                String text = extractTextPage(document, page + 1);
                pageTexts[page] = text;
                int effectiveLen = effectiveLength(text);
                if (effectiveLen >= scanThreshold) {
                    isScanned[page] = false;
                } else if (effectiveLen > 0 && hasDocTitle) {
                    // 封面/标题页例外：有元数据标题且页内含少量文字 → 按文本页处理，不送 OCR
                    isScanned[page] = false;
                } else {
                    isScanned[page] = true;
                    scannedCount++;
                    if (scannedCount > maxOcrPages) {
                        throw new IllegalArgumentException(
                                "扫描页数超过上限(" + maxOcrPages + ")，请将文档拆分为多个文件后分别上传");
                    }
                }
            }

            // 第二遍第一阶段：扫描页 OCR（fail-fast；空白页跳过），得到全部页最终文本
            List<String> warnings = new ArrayList<>();
            for (int page = 0; page < totalPages; page++) {
                if (isScanned[page]) {
                    String ocrText = ocrPageOrThrow(document, page, warnings);
                    pageTexts[page] = ocrText == null ? "" : ocrText;
                }
            }
            // 页眉页脚清洗（在含 OCR 的最终文本上执行）
            stripHeadersFooters(pageTexts);
            // 全文是否有多行内容：首行裸标题仅在存在后续内容时才提升为根（避免单行文档被吞作标题）
            boolean hasFollowingContent = countNonEmptyLines(pageTexts) >= 2;
            // 第二遍第二阶段：逐页按标题切块（页内切块 + 标题栈跨页继承 + 首行裸标题作根）
            List<ParsedBlock> blocks = new ArrayList<>();
            Deque<ParsedHeading> stack = new ArrayDeque<>();
            for (int page = 0; page < totalPages; page++) {
                String text = pageTexts[page];
                if (text == null || text.isBlank()) {
                    continue;
                }
                splitPageIntoBlocks(blocks, stack, text, page + 1, hasFollowingContent);
            }
            int totalChars = blocks.stream().mapToInt(b -> b.text().length()).sum();
            return new ParsedDocument(blocks, totalChars, warnings);
        }
    }

    /** 用 PDFTextStripper 抽取单页文本 */
    private String extractTextPage(PDDocument document, int pageNo) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        String text = stripper.getText(document);
        return text == null ? "" : text.trim();
    }

    /** 有效可见字符数：剔除控制/格式/空白/乱码替换符后的长度，避免虚高误判为文本页 */
    static int effectiveLength(String text) {
        return text == null ? 0 : text.replaceAll("[\\p{C}\\s\\uFFFD]", "").length();
    }

    /** 标题行识别：短行、无句尾标点、非页码/符号行、匹配标题形态 */
    static boolean isHeadingLine(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.length() > MAX_HEADING_LEN) {
            return false;
        }
        if (ENDS_WITH_SENTENCE.matcher(t).matches()) {
            return false;
        }
        if (PAGE_NUMBER_LINE.matcher(t).matches()) {
            return false;
        }
        if (SYMBOL_LINE.matcher(t).matches()) {
            return false;
        }
        return HEADING_PATTERN.matcher(t).find();
    }

    /** 标题层级推断（用于标题栈）：第X章/中文序数=1，括号形态=2，多级数字=段数+1 */
    static int headingLevelOf(String heading) {
        String t = heading.trim();
        if (t.matches("^第[一二三四五六七八九十百零\\d]+[章节条部分].*")) {
            return 1;
        }
        if (t.matches("^[一二三四五六七八九十]+[、．.].*")) {
            return 1;
        }
        if (t.matches("^[【「].*") || t.matches("^[（(].*")) {
            return 2;
        }
        Matcher m = DECIMAL_HEADING.matcher(t);
        if (m.find()) {
            return m.group(1).split("\\.").length + 1;
        }
        return 2;
    }

    /** 裸文字标题识别：短、无句尾标点、非页码/符号、非编号标题，且至少 2 字（用于首行根标题） */
    static boolean looksLikeTitle(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.length() > MAX_HEADING_LEN || t.length() < 2) {
            return false;
        }
        if (ENDS_WITH_SENTENCE.matcher(t).matches()) {
            return false;
        }
        if (PAGE_NUMBER_LINE.matcher(t).matches()) {
            return false;
        }
        if (SYMBOL_LINE.matcher(t).matches()) {
            return false;
        }
        return !HEADING_PATTERN.matcher(t).find();
    }

    /** 页脚行识别：以"第 N 页"结尾的短行（如"翠湖花园办事指南 第2页"） */
    static boolean isFooterPageLine(String line) {
        return FOOTER_PAGE_LINE.matcher(line.trim()).matches();
    }

    /**
     * 页眉页脚清洗：先剔除"第N页"页脚行（使页眉在续页回到页首位置），再按"页首/页末位置重复"
     * 判定页眉页脚——页中重复出现的章节标题（如多个一级章节下都有的【所需材料】）不受影响。
     */
    private void stripHeadersFooters(String[] pageTexts) {
        // 第一步：剔除"第N页"页脚行
        for (int p = 0; p < pageTexts.length; p++) {
            pageTexts[p] = filterLines(pageTexts[p], PdfParser::isFooterPageLine);
        }
        // 第二步：按页首/页末位置统计，重复出现（>=2 个不同页）的判为页眉/页脚
        Map<String, Integer> firstLineCount = new HashMap<>();
        Map<String, Integer> lastLineCount = new HashMap<>();
        for (String text : pageTexts) {
            String first = firstLineOf(text);
            String last = lastLineOf(text);
            if (first != null) {
                firstLineCount.merge(first, 1, Integer::sum);
            }
            if (last != null) {
                lastLineCount.merge(last, 1, Integer::sum);
            }
        }
        Set<String> headers = firstLineCount.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<String> footers = lastLineCount.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        for (int p = 0; p < pageTexts.length; p++) {
            pageTexts[p] = filterLines(pageTexts[p], t -> headers.contains(t) || footers.contains(t));
        }
    }

    /** 过滤行：剔除空行与满足 remove 条件的行，按换行重新拼接 */
    private static String filterLines(String text, Predicate<String> remove) {
        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || remove.test(t)) {
                continue;
            }
            if (cleaned.length() > 0) {
                cleaned.append('\n');
            }
            cleaned.append(t);
        }
        return cleaned.toString();
    }

    /** 页首首个非空行 */
    private static String firstLineOf(String text) {
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /** 页末最后一个非空行 */
    private static String lastLineOf(String text) {
        String last = null;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                last = t;
            }
        }
        return last;
    }

    /** 统计全文非空行数（判断是否存在后续内容，供首行裸标题提升判断） */
    private static int countNonEmptyLines(String[] pageTexts) {
        int count = 0;
        for (String text : pageTexts) {
            for (String line : text.split("\n")) {
                if (!line.trim().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 页内按标题切块：识别标题行更新标题栈并封块，其余正文累积入当前块 */
    private void splitPageIntoBlocks(List<ParsedBlock> blocks, Deque<ParsedHeading> stack,
                                     String text, int pageNo, boolean hasFollowingContent) {
        StringBuilder current = new StringBuilder();
        String blockHeading = pathOf(stack);
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 文档首行裸文字标题（无编号/括号）且在存在后续内容时才提升为 0 级根标题
            boolean isBareTitle = stack.isEmpty() && current.length() == 0
                    && hasFollowingContent && looksLikeTitle(line);
            if (isHeadingLine(line) || isBareTitle) {
                flushBlock(blocks, blockHeading, current, pageNo);
                if (isBareTitle) {
                    stack.addLast(new ParsedHeading(0, line));
                } else {
                    pushHeading(stack, line);
                }
                blockHeading = pathOf(stack);
            } else {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        flushBlock(blocks, blockHeading, current, pageNo);
    }

    /** 标题入栈：弹出所有层级 ≥ 当前层级的旧标题，再压入新标题 */
    private static void pushHeading(Deque<ParsedHeading> stack, String heading) {
        int level = headingLevelOf(heading);
        while (!stack.isEmpty() && stack.peekLast().level() >= level) {
            stack.removeLast();
        }
        stack.addLast(new ParsedHeading(level, heading));
    }

    /** 标题栈拼接完整路径（父级在前），超长截断 */
    private static String pathOf(Deque<ParsedHeading> stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String path = stack.stream().map(ParsedHeading::title).collect(Collectors.joining(" / "));
        return path.length() > MAX_SECTION_PATH ? path.substring(0, MAX_SECTION_PATH) : path;
    }

    /** 累积的正文作为一个块落盘并清空 */
    private static void flushBlock(List<ParsedBlock> blocks, String heading, StringBuilder current, int pageNo) {
        String content = current.toString().trim();
        if (!content.isEmpty()) {
            blocks.add(new ParsedBlock(heading, 0, content, pageNo));
        }
        current.setLength(0);
    }

    /** OCR 扫描页：异常 fail-fast 抛友好异常；空结果视为空白页返回 null（warning 已记录） */
    private String ocrPageOrThrow(PDDocument document, int pageIndex, List<String> warnings) {
        try {
            String ocrText = ocrPage(document, pageIndex);
            if (ocrText == null || ocrText.isBlank()) {
                warnings.add("第 " + (pageIndex + 1) + " 页未识别到文字，已作为空白页跳过");
                return null;
            }
            return ocrText;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("第 " + (pageIndex + 1) + " 页 OCR 识别失败，请稍后重试");
        }
    }

    /** 渲染页面为 JPEG 并调用 OCR；图片过大时降 DPI 重试一次 */
    private String ocrPage(PDDocument document, int pageIndex) {
        float dpi = ocrDpi;
        for (int attempt = 0; attempt < 2; attempt++) {
            byte[] imageBytes = renderPage(document, pageIndex, dpi);
            if (imageBytes.length > MAX_IMAGE_BYTES) {
                dpi -= 50;
                if (dpi < 60) {
                    break;
                }
                continue;
            }
            if (imageBytes.length == 0) {
                return null;
            }
            return visionOcrClient.ocrImage(imageBytes, "image/jpeg");
        }
        return null;
    }

    /** 用 PDFRenderer 把页面渲染为 JPEG 字节 */
    private byte[] renderPage(PDDocument document, int pageIndex, float dpi) {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("PDF 页面渲染失败: page={}", pageIndex + 1, e);
            return new byte[0];
        }
    }

    /** 标题栈条目：层级 + 标题文本 */
    private record ParsedHeading(int level, String title) {
    }
}
