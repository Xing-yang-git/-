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
 * CsvParser 单元测试 — 覆盖表头+行分组、全空白字段行跳过、空 CSV 告警。
 */
@DisplayName("CsvParser CSV 解析器单元测试")
class CsvParserTest {

    @TempDir
    Path tempDir;

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("data.csv");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    @DisplayName("解析 - 首行作表头，数据行按 rows-per-chunk 分组为一块")
    void should_groupByHeader_when_rowsUnderLimit() throws Exception {
        CsvParser parser = new CsvParser(3);
        ParsedDocument doc = parser.parse(write("姓名,电话\n张三,138\n李四,139"));

        assertThat(doc.blocks()).hasSize(1);
        ParsedBlock block = doc.blocks().get(0);
        assertThat(block.text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138\n李四 | 139");
        assertThat(doc.totalChars()).isEqualTo(block.text().length());
    }

    @Test
    @DisplayName("解析 - 数据行超过 rows-per-chunk 时拆分为多块，每块含表头")
    void should_splitIntoMultipleBlocks_when_overLimit() throws Exception {
        CsvParser parser = new CsvParser(1);
        ParsedDocument doc = parser.parse(write("姓名,电话\n张三,138\n李四,139"));

        assertThat(doc.blocks()).hasSize(2);
        assertThat(doc.blocks().get(0).text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138");
        assertThat(doc.blocks().get(1).text()).isEqualTo("表头: 姓名 | 电话\n李四 | 139");
    }

    @Test
    @DisplayName("解析 - 全空白字段行被跳过")
    void should_skipBlankRecord_when_allFieldsBlank() throws Exception {
        CsvParser parser = new CsvParser(3);
        ParsedDocument doc = parser.parse(write("姓名,电话\n张三,138\n,,\n李四,139"));

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138\n李四 | 139");
    }

    @Test
    @DisplayName("解析 - 空 CSV 返回空块并告警")
    void should_returnEmptyWithWarning_when_csvEmpty() throws Exception {
        CsvParser parser = new CsvParser(3);
        ParsedDocument doc = parser.parse(write(""));

        assertThat(doc.blocks()).isEmpty();
        assertThat(doc.warnings()).containsExactly("CSV 为空，未提取到任何数据");
    }
}
