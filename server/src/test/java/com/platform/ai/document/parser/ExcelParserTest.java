package com.platform.ai.document.parser;

import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExcelParser 单元测试 — 覆盖表头+行分组、空行跳过、空表告警（内存构造 xlsx，不依赖真实文件）。
 */
@DisplayName("ExcelParser Excel 解析器单元测试")
class ExcelParserTest {

    @TempDir
    Path tempDir;

    /** 用 POI 在内存构造 xlsx 并落盘 */
    private Path writeXlsx(String[][] rows) throws Exception {
        Path file = tempDir.resolve("数据.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                String[] cells = rows[r];
                if (cells == null) {
                    continue; // 全空行
                }
                for (int c = 0; c < cells.length; c++) {
                    row.createCell(c).setCellValue(cells[c]);
                }
            }
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }
        return file;
    }

    @Test
    @DisplayName("解析 - 首个非空行作表头，数据行分组生成一块")
    void should_groupRowsWithHeader_when_underLimit() throws Exception {
        ExcelParser parser = new ExcelParser(3);
        ParsedDocument doc = parser.parse(writeXlsx(new String[][]{
                {"姓名", "电话"},
                {"张三", "138"},
                {"李四", "139"}
        }));

        assertThat(doc.blocks()).hasSize(1);
        ParsedBlock block = doc.blocks().get(0);
        assertThat(block.text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138\n李四 | 139");
    }

    @Test
    @DisplayName("解析 - 数据行超过 rows-per-chunk 拆分为多块，每块带表头")
    void should_splitIntoMultipleBlocks_when_overLimit() throws Exception {
        ExcelParser parser = new ExcelParser(1);
        ParsedDocument doc = parser.parse(writeXlsx(new String[][]{
                {"姓名", "电话"},
                {"张三", "138"},
                {"李四", "139"}
        }));

        assertThat(doc.blocks()).hasSize(2);
        assertThat(doc.blocks().get(0).text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138");
        assertThat(doc.blocks().get(1).text()).isEqualTo("表头: 姓名 | 电话\n李四 | 139");
    }

    @Test
    @DisplayName("解析 - 空行（无单元格）被跳过不参与分组")
    void should_skipBlankRow_when_noCells() throws Exception {
        ExcelParser parser = new ExcelParser(3);
        ParsedDocument doc = parser.parse(writeXlsx(new String[][]{
                {"姓名", "电话"},
                {"张三", "138"},
                null, // 空行
                {"李四", "139"}
        }));

        assertThat(doc.blocks()).hasSize(1);
        assertThat(doc.blocks().get(0).text()).isEqualTo("表头: 姓名 | 电话\n张三 | 138\n李四 | 139");
    }

    @Test
    @DisplayName("解析 - 空工作表返回空块并告警")
    void should_returnEmptyWithWarning_when_sheetEmpty() throws Exception {
        ExcelParser parser = new ExcelParser(3);
        Path file = tempDir.resolve("空.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("Sheet1");
            try (var out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        }

        ParsedDocument doc = parser.parse(file);

        assertThat(doc.blocks()).isEmpty();
        assertThat(doc.warnings()).containsExactly("工作表为空，未提取到任何数据");
    }
}
