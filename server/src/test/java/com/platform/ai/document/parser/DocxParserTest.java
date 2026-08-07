package com.platform.ai.document.parser;

import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocxParser 单元测试 — 覆盖 Heading 样式切块、标题栈路径、无标题告警、表格文本（内存构造 docx）。
 */
@DisplayName("DocxParser Word 解析器单元测试")
class DocxParserTest {

    @TempDir
    Path tempDir;

    /** 用 POI 在内存构造 docx 并落盘 */
    private Path writeDocx(java.util.function.Consumer<XWPFDocument> fill) throws Exception {
        Path file = tempDir.resolve("文档.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            fill.accept(document);
            try (var out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        return file;
    }

    /** 新增一个指定样式的标题段落 */
    private void heading(XWPFDocument doc, String style, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle(style);
        p.createRun().setText(text);
    }

    /** 新增一个普通正文段落 */
    private void body(XWPFDocument doc, String text) {
        doc.createParagraph().createRun().setText(text);
    }

    @Test
    @DisplayName("解析 - Heading 样式段落切块，同级标题相互覆盖")
    void should_splitByHeadingStyle_when_headingsPresent() throws Exception {
        Path file = writeDocx(doc -> {
            heading(doc, "Heading1", "第一章");
            body(doc, "正文一");
            heading(doc, "Heading1", "第二章");
            body(doc, "正文二");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).hasSize(2);
        ParsedBlock first = doc.blocks().get(0);
        assertThat(first.sectionTitle()).isEqualTo("第一章");
        assertThat(first.headingLevel()).isEqualTo(1);
        assertThat(first.text()).isEqualTo("正文一");
        ParsedBlock second = doc.blocks().get(1);
        assertThat(second.sectionTitle()).isEqualTo("第二章");
        assertThat(second.headingLevel()).isEqualTo(1);
        assertThat(second.text()).isEqualTo("正文二");
        assertThat(doc.warnings()).isEmpty();
    }

    @Test
    @DisplayName("解析 - 嵌套标题保留完整路径")
    void should_buildHeadingStack_when_nestedHeadings() throws Exception {
        Path file = writeDocx(doc -> {
            heading(doc, "Heading1", "第一章");
            body(doc, "总则内容");
            heading(doc, "Heading2", "第一节");
            body(doc, "第一节内容");
            heading(doc, "Heading3", "1.1 步骤");
            body(doc, "步骤内容");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).extracting(ParsedBlock::sectionTitle)
                .containsExactly("第一章", "第一章 / 第一节", "第一章 / 第一节 / 1.1 步骤");
        assertThat(doc.blocks()).extracting(ParsedBlock::headingLevel)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("解析 - Title 样式作为 0 级根，Heading 挂在其下")
    void should_treatTitleStyleAsRoot() throws Exception {
        Path file = writeDocx(doc -> {
            heading(doc, "Title", "翠湖花园小区规章制度");
            body(doc, "制定依据导语");
            heading(doc, "Heading1", "一、门禁与访客管理");
            body(doc, "门禁内容");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).hasSize(2);
        assertThat(doc.blocks().get(0).sectionTitle()).isEqualTo("翠湖花园小区规章制度");
        assertThat(doc.blocks().get(0).text()).isEqualTo("制定依据导语");
        assertThat(doc.blocks().get(1).sectionTitle()).isEqualTo("翠湖花园小区规章制度 / 一、门禁与访客管理");
        assertThat(doc.blocks().get(1).headingLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("解析 - 章节路径超 200 字符截断")
    void should_truncateSectionPath_when_exceedsLimit() throws Exception {
        String longHeading = "超长标题".repeat(60); // 240 字符
        Path file = writeDocx(doc -> {
            heading(doc, "Heading1", longHeading);
            body(doc, "内容");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).hasSize(200);
        assertThat(doc.blocks().get(0).sectionTitle()).startsWith("超长标题");
    }

    @Test
    @DisplayName("解析 - 无任何标题样式的文档退化为单块并告警")
    void should_fallbackSingleBlockWithWarning_when_noHeadingStyle() throws Exception {
        Path file = writeDocx(doc -> {
            body(doc, "纯正文段落");
            body(doc, "第二段");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).isNull();
        assertThat(doc.blocks().get(0).text()).isEqualTo("纯正文段落\n第二段");
        assertThat(doc.warnings()).hasSize(1);
        assertThat(doc.warnings().get(0)).contains("文档无标题样式");
    }

    @Test
    @DisplayName("解析 - 表格文本按行列提取并归入当前章节")
    void should_extractTableText_when_tablePresent() throws Exception {
        Path file = writeDocx(doc -> {
            heading(doc, "Heading1", "联系方式");
            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("姓名");
            table.getRow(0).getCell(1).setText("电话");
            table.getRow(1).getCell(0).setText("张三");
            table.getRow(1).getCell(1).setText("138");
        });

        ParsedDocument doc = new DocxParser().parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).isEqualTo("联系方式");
        assertThat(doc.blocks().get(0).text()).isEqualTo("姓名 | 电话\n张三 | 138");
    }
}
