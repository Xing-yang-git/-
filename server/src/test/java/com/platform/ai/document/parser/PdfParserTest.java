package com.platform.ai.document.parser;

import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.ai.document.ocr.VisionOcrClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PdfParser 单元测试 — 用 PDFBox 内存构造极小 PDF，覆盖文本页抽取、扫描页 OCR、标题识别与切块、
 * 跨页继承、扫描页超限拒绝、封面例外、OCR 失败处理、加密异常。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfParser PDF 解析器单元测试")
class PdfParserTest {

    @Mock
    private VisionOcrClient visionOcrClient;

    @TempDir
    Path tempDir;

    @FunctionalInterface
    private interface PdfWriter {
        void write(PDDocument document) throws Exception;
    }

    private Path writePdf(PdfWriter fill) throws Exception {
        Path file = tempDir.resolve("doc.pdf");
        try (PDDocument document = new PDDocument()) {
            fill.write(document);
            document.save(file.toFile());
        }
        return file;
    }

    /** 追加一页，逐行绘制 ASCII 文本（标题/正文以独立行呈现，便于 PDFTextStripper 按行抽取） */
    private void addTextPage(PDDocument doc, String... lines) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(50, 700);
            for (String line : lines) {
                cs.showText(line);
                cs.newLineAtOffset(0, -20);
            }
            cs.endText();
        }
    }

    // ==================== 标题启发式（直接方法级测试，无需 PDF 字体） ====================

    @Test
    @DisplayName("标题启发式 - 编号/括号形态识别为标题，正文行否")
    void should_detectHeadingForms() {
        assertThat(PdfParser.isHeadingLine("一、居住证办理")).isTrue();
        assertThat(PdfParser.isHeadingLine("【办理条件】")).isTrue();
        assertThat(PdfParser.isHeadingLine("（一）")).isTrue();
        assertThat(PdfParser.isHeadingLine("第3章 总则")).isTrue();
        assertThat(PdfParser.isHeadingLine("1.1 注册流程")).isTrue();
        assertThat(PdfParser.isHeadingLine("1. 在翠湖花园实际居住满6个月以上的非本市户籍人员；")).isFalse();
        assertThat(PdfParser.isHeadingLine("翠湖花园办事指南")).isFalse();
        assertThat(PdfParser.isHeadingLine("3")).isFalse();
        assertThat(PdfParser.isHeadingLine("---")).isFalse();
        assertThat(PdfParser.isHeadingLine("这是正文内容，不是标题。")).isFalse();
    }

    @Test
    @DisplayName("标题层级推断 - 序数/第X章=1，括号=2，多级数字=段数+1")
    void should_inferHeadingLevels() {
        assertThat(PdfParser.headingLevelOf("一、居住证办理")).isEqualTo(1);
        assertThat(PdfParser.headingLevelOf("第3章 总则")).isEqualTo(1);
        assertThat(PdfParser.headingLevelOf("【办理条件】")).isEqualTo(2);
        assertThat(PdfParser.headingLevelOf("（一）")).isEqualTo(2);
        assertThat(PdfParser.headingLevelOf("1.1 注册流程")).isEqualTo(3);
        assertThat(PdfParser.headingLevelOf("1.1.1 步骤")).isEqualTo(4);
    }

    @Test
    @DisplayName("有效字符 - 剔除空白/控制符，避免虚高")
    void should_countEffectiveChars() {
        assertThat(PdfParser.effectiveLength("  \n\t  ")).isZero();
        // 空格属于 \s 被剔除，仅统计可见字符
        assertThat(PdfParser.effectiveLength("Hello World")).isEqualTo(10);
        // 控制字符/零宽字符在运行时构造，避免源码内嵌不可见字符
        assertThat(PdfParser.effectiveLength("a" + (char) 0 + "b" + (char) 0x200B + "c")).isEqualTo(3);
    }

    // ==================== 解析流程 ====================

    @Test
    @DisplayName("解析 - 抽取文本量达到阈值时作为文本页，不触发 OCR")
    void should_extractTextPage_when_textAboveThreshold() throws Exception {
        Path file = writePdf(doc -> addTextPage(doc, "Hello World"));
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(1);
        ParsedBlock block = parsed.blocks().get(0);
        assertThat(block.text()).isEqualTo("Hello World");
        assertThat(block.pageNo()).isEqualTo(1);
        assertThat(parsed.warnings()).isEmpty();
        verifyNoInteractions(visionOcrClient);
    }

    @Test
    @DisplayName("解析 - 抽取文本低于阈值判定扫描页，走 OCR")
    void should_ocrScanPage_when_textBelowThreshold() throws Exception {
        Path file = writePdf(doc -> doc.addPage(new PDPage())); // 空白页无文本
        when(visionOcrClient.ocrImage(any(byte[].class), anyString())).thenReturn("OCR 识别文字");
        PdfParser parser = new PdfParser(visionOcrClient, 20, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(1);
        assertThat(parsed.blocks().get(0).text()).isEqualTo("OCR 识别文字");
        assertThat(parsed.blocks().get(0).pageNo()).isEqualTo(1);
        assertThat(parsed.warnings()).isEmpty();
    }

    @Test
    @DisplayName("解析 - 加密文档抛出 IllegalArgumentException")
    void should_throwEncrypted_when_documentEncrypted() throws Exception {
        Path file = writePdf(doc -> {
            doc.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pass", "", new AccessPermission());
            doc.protect(policy);
        });
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件加密");
    }

    @Test
    @DisplayName("解析 - 扫描页数超上限直接拒绝，零 OCR 调用")
    void should_throwWhenScanCountExceedsLimit() throws Exception {
        Path file = writePdf(doc -> {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
        });
        PdfParser parser = new PdfParser(visionOcrClient, 20, 150, 2);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扫描页数超过上限(2)");
        verifyNoInteractions(visionOcrClient);
    }

    @Test
    @DisplayName("解析 - 元数据标题 + 短文本页按文本页处理，不触发 OCR")
    void should_treatShortPageAsText_when_docTitlePresent() throws Exception {
        Path file = writePdf(doc -> {
            doc.getDocumentInformation().setTitle("翠湖花园办事指南");
            addTextPage(doc, "Guide");
        });
        PdfParser parser = new PdfParser(visionOcrClient, 20, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(1);
        assertThat(parsed.blocks().get(0).text()).isEqualTo("Guide");
        verifyNoInteractions(visionOcrClient);
    }

    @Test
    @DisplayName("解析 - 页内按标题切块，父级路径保留")
    void should_splitPageAtHeadings() throws Exception {
        Path file = writePdf(doc -> addTextPage(doc,
                "1.1 Condition A",
                "item one;",
                "item two.",
                "1.2 Condition B",
                "item three."));
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(2);
        ParsedBlock first = parsed.blocks().get(0);
        assertThat(first.sectionTitle()).isEqualTo("1.1 Condition A");
        assertThat(first.text()).isEqualTo("item one;\nitem two.");
        ParsedBlock second = parsed.blocks().get(1);
        assertThat(second.sectionTitle()).isEqualTo("1.2 Condition B");
        assertThat(second.text()).isEqualTo("item three.");
        assertThat(second.pageNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("解析 - 标题跨页继承，后续页分点归属同一标题")
    void should_inheritHeadingAcrossPages() throws Exception {
        Path file = writePdf(doc -> {
            addTextPage(doc, "1.1 Condition A", "item one;");
            addTextPage(doc, "item two.");
        });
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).sectionTitle()).isEqualTo("1.1 Condition A");
        assertThat(parsed.blocks().get(0).pageNo()).isEqualTo(1);
        assertThat(parsed.blocks().get(1).sectionTitle()).isEqualTo("1.1 Condition A");
        assertThat(parsed.blocks().get(1).pageNo()).isEqualTo(2);
        assertThat(parsed.blocks().get(1).text()).isEqualTo("item two.");
    }

    @Test
    @DisplayName("解析 - 单页 OCR 异常 fail-fast 整篇失败")
    void should_failWholeDoc_when_ocrThrows() throws Exception {
        Path file = writePdf(doc -> doc.addPage(new PDPage()));
        when(visionOcrClient.ocrImage(any(byte[].class), anyString()))
                .thenThrow(new RuntimeException("API 熔断"));
        PdfParser parser = new PdfParser(visionOcrClient, 20, 150, 20);

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("第 1 页 OCR 识别失败");
    }

    @Test
    @DisplayName("解析 - OCR 空结果视为空白页跳过并告警")
    void should_skipBlankOcrPage_withWarning() throws Exception {
        Path file = writePdf(doc -> doc.addPage(new PDPage()));
        when(visionOcrClient.ocrImage(any(byte[].class), anyString())).thenReturn("   ");
        PdfParser parser = new PdfParser(visionOcrClient, 20, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).isEmpty();
        assertThat(parsed.warnings()).hasSize(1);
        assertThat(parsed.warnings().get(0)).contains("未识别到文字");
    }

    @Test
    @DisplayName("页脚识别 - 多种页码格式")
    void should_detectFooterPageLine() {
        assertThat(PdfParser.isFooterPageLine("翠湖花园办事指南 第2页")).isTrue();
        assertThat(PdfParser.isFooterPageLine("第 2 页 共 5 页")).isTrue();
        assertThat(PdfParser.isFooterPageLine("Page 3")).isTrue();
        assertThat(PdfParser.isFooterPageLine("Page 3 of 5")).isTrue();
        assertThat(PdfParser.isFooterPageLine("3/20")).isTrue();
        assertThat(PdfParser.isFooterPageLine("- 3 -")).isTrue();
        assertThat(PdfParser.isFooterPageLine("这是正文内容，不应删除。")).isFalse();
        assertThat(PdfParser.isFooterPageLine("联系电话 400-168-6688")).isFalse();
        assertThat(PdfParser.isFooterPageLine("2024/1/3")).isFalse(); // 日期含两个斜杠，不误判
    }

    @Test
    @DisplayName("裸标题识别 - 短无标点的首行文字可作根标题")
    void should_detectBareTitle() {
        assertThat(PdfParser.looksLikeTitle("翠湖花园办事指南")).isTrue();
        assertThat(PdfParser.looksLikeTitle("Guide Title")).isTrue();
        assertThat(PdfParser.looksLikeTitle("一、居住证办理")).isFalse(); // 已是编号标题
        assertThat(PdfParser.looksLikeTitle("这是正文内容，不是标题。")).isFalse(); // 句尾标点
        assertThat(PdfParser.looksLikeTitle("item one;")).isFalse();
        assertThat(PdfParser.looksLikeTitle("3")).isFalse(); // 页码
    }

    @Test
    @DisplayName("解析 - 页首重复行作为页眉剔除，页中重复标题保留")
    void should_stripRepeatedHeaderAcrossPages() throws Exception {
        Path file = writePdf(doc -> {
            addTextPage(doc, "HEADER", "1.1 Section A", "item one;");
            addTextPage(doc, "HEADER", "1.1 Section B", "item two.");
        });
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).text()).isEqualTo("item one;");
        assertThat(parsed.blocks().get(1).text()).isEqualTo("item two.");
        // 页首 HEADER 被剔除；页中重复的章节标题仍被识别
        assertThat(parsed.blocks().get(0).sectionTitle()).isEqualTo("1.1 Section A");
        assertThat(parsed.blocks().get(1).sectionTitle()).isEqualTo("1.1 Section B");
    }

    @Test
    @DisplayName("解析 - 页中重复出现的同一章节标题不被当作页眉剔除")
    void should_keepRepeatedMidPageHeading() throws Exception {
        Path file = writePdf(doc -> {
            addTextPage(doc, "HEADER", "1.1 Sub", "item one;");
            addTextPage(doc, "HEADER", "1.1 Sub", "item two.");
        });
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).sectionTitle()).isEqualTo("1.1 Sub");
        assertThat(parsed.blocks().get(1).sectionTitle()).isEqualTo("1.1 Sub");
        assertThat(parsed.blocks().get(0).text()).isEqualTo("item one;");
    }

    @Test
    @DisplayName("解析 - 文档首行裸标题作为 0 级根，后续章节挂在其下")
    void should_promoteFirstBareTitleAsRoot() throws Exception {
        Path file = writePdf(doc -> addTextPage(doc,
                "Guide Title",
                "intro line.",
                "1.1 Section A",
                "item one;"));
        PdfParser parser = new PdfParser(visionOcrClient, 5, 150, 20);

        ParsedDocument parsed = parser.parse(file);

        assertThat(parsed.blocks()).hasSize(2);
        assertThat(parsed.blocks().get(0).sectionTitle()).isEqualTo("Guide Title");
        assertThat(parsed.blocks().get(0).text()).isEqualTo("intro line.");
        assertThat(parsed.blocks().get(1).sectionTitle()).isEqualTo("Guide Title / 1.1 Section A");
        assertThat(parsed.blocks().get(1).text()).isEqualTo("item one;");
    }
}
