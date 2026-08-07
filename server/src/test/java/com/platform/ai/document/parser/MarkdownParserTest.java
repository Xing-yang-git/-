package com.platform.ai.document.parser;

import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarkdownParser 单元测试 — 覆盖标题栈路径、脏标签清洗、分割线处理、无标题单块。
 */
@DisplayName("MarkdownParser Markdown 解析器单元测试")
class MarkdownParserTest {

    @TempDir
    Path tempDir;

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("doc.md");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("解析 - 按标题层级切块并保留完整路径与叶子层级")
    void should_splitByHeading_when_headingsPresent() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("# 第一章\n内容一\n## 二级标题\n内容二");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(2);
        ParsedBlock first = doc.blocks().get(0);
        assertThat(first.sectionTitle()).isEqualTo("第一章");
        assertThat(first.headingLevel()).isEqualTo(1);
        assertThat(first.text()).isEqualTo("内容一");
        ParsedBlock second = doc.blocks().get(1);
        assertThat(second.sectionTitle()).isEqualTo("第一章 / 二级标题");
        assertThat(second.headingLevel()).isEqualTo(2);
        assertThat(second.text()).isEqualTo("内容二");
        assertThat(doc.totalChars()).isEqualTo("内容一内容二".length());
    }

    @Test
    @DisplayName("解析 - 嵌套标题栈保留路径，同级/回退弹出")
    void should_buildHeadingStack_when_nestedAndSiblings() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("# A\n文本a\n## B\n文本b\n### C\n文本c\n## D\n文本d\n# E\n文本e");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).extracting(ParsedBlock::sectionTitle)
                .containsExactly("A", "A / B", "A / B / C", "A / D", "E");
        assertThat(doc.blocks()).extracting(ParsedBlock::headingLevel)
                .containsExactly(1, 2, 3, 2, 1);
    }

    @Test
    @DisplayName("解析 - 标题文本过 markdown 清洗")
    void should_stripMarkdownInHeading_when_present() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("# **加粗标题**\n内容");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).isEqualTo("加粗标题");
    }

    @Test
    @DisplayName("解析 - 章节路径超 200 字符截断")
    void should_truncateSectionPath_when_exceedsLimit() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        String heading = "# " + "超长标题".repeat(60); // 240 字符
        Path file = write(heading + "\n内容");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).hasSize(200);
        assertThat(doc.blocks().get(0).sectionTitle()).startsWith("超长标题");
    }

    @Test
    @DisplayName("解析 - 脏标签（图片/链接/强调/列表）被清洗为纯文本")
    void should_stripMarkdownDirtyTags_when_present() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("![图片](a.png)\n[链接](http://x.com)\n**加粗** 和 `代码` 和 *斜体*\n- 列表项");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text())
                .isEqualTo("链接\n加粗 和 代码 和 斜体\n列表项");
    }

    @Test
    @DisplayName("解析 - 夹在正文之间的分割线作为业务分隔保留")
    void should_keepDivider_when_sandwichedByBody() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("正文一\n---\n正文二");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text()).isEqualTo("正文一\n---\n正文二");
    }

    @Test
    @DisplayName("解析 - *** 与 - - - 变体同样保留/删除")
    void should_handleDividerVariants_when_sandwiched() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("正文一\n***\n正文二\n\n- - -\n正文三");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text())
                .isEqualTo("正文一\n***\n正文二\n- - -\n正文三");
    }

    @Test
    @DisplayName("解析 - 紧邻标题的分割线视为分页残留删除")
    void should_deleteDivider_when_adjacentToHeading() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("正文一\n---\n## 标题\n内容");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(2);
        assertThat(doc.blocks().get(0).sectionTitle()).isNull();
        assertThat(doc.blocks().get(0).text()).isEqualTo("正文一");
        assertThat(doc.blocks().get(1).sectionTitle()).isEqualTo("标题");
        assertThat(doc.blocks().get(1).text()).isEqualTo("内容");
    }

    @Test
    @DisplayName("解析 - 文件开头/结尾的纯符号行删除")
    void should_deleteDivider_atDocumentBoundary() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("---\n正文\n***");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text()).isEqualTo("正文");
    }

    @Test
    @DisplayName("解析 - 无标题的正文合并为单块，section 为 null")
    void should_mergeIntoSingleBlock_when_noHeading() throws Exception {
        MarkdownParser parser = new MarkdownParser();
        Path file = write("只有正文内容\n第二行正文");

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).isNull();
        assertThat(doc.blocks().get(0).text()).isEqualTo("只有正文内容\n第二行正文");
    }
}
