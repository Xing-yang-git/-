package com.platform.ai.document.parser;

import com.platform.ai.document.ParsedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextParser 纯文本解析器单元测试 — 覆盖整篇单块、文件名作章节名、空文件。
 */
@DisplayName("TextParser 纯文本解析器单元测试")
class TextParserTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("解析 - 整篇文本作为单块，章节名取文件名")
    void should_parseWholeAsSingleBlock_when_validText() throws Exception {
        TextParser parser = new TextParser();
        Path file = tempDir.resolve("服务手册.txt");
        Files.write(file, "第一条\n第二条".getBytes(Charset.forName("UTF-8")));

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).sectionTitle()).isEqualTo("服务手册");
        assertThat(doc.blocks().get(0).text()).isEqualTo("第一条\n第二条");
        assertThat(doc.totalChars()).isEqualTo("第一条\n第二条".length());
    }

    @Test
    @DisplayName("解析 - 文件名含多段点号仅去末段扩展名")
    void should_stripOnlyLastExtension_when_multipleDots() throws Exception {
        TextParser parser = new TextParser();
        Path file = tempDir.resolve("物业.v1.说明.txt");
        Files.write(file, "正文".getBytes(Charset.forName("UTF-8")));

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks().get(0).sectionTitle()).isEqualTo("物业.v1.说明");
    }

    @Test
    @DisplayName("解析 - 空文件返回空块")
    void should_returnEmpty_when_emptyFile() throws Exception {
        TextParser parser = new TextParser();
        Path file = tempDir.resolve("空.txt");
        Files.write(file, new byte[0]);

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).isEmpty();
        assertThat(doc.totalChars()).isZero();
    }
}
