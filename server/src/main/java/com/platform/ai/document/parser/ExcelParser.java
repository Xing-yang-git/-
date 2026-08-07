package com.platform.ai.document.parser;

import com.alibaba.excel.EasyExcel;
import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.common.KnowledgeFileType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel 解析器（xlsx）— 读首个工作表，首非空行作表头，之后每 rows-per-chunk 行一组生成一个块。
 *
 * <p>块内容 = 表头行 + 数据行，保证行内字段语义在检索时可见；空行自动跳过。</p>
 * <p>【暂时禁用】XLSX 类型暂不支持（Controller 已拦截上传），恢复时取消下方 {@code @Component} 注释即可，
 * 类保留以便单元测试直接构造。</p>
 */
// @Component
public class ExcelParser implements DocumentParser {

    /** 每组数据行数 */
    private final int rowsPerChunk;

    /**
     * 构造器注入。
     *
     * @param rowsPerChunk 每组数据行数（ai.doc.rows-per-chunk）
     */
    public ExcelParser(@Value("${ai.doc.rows-per-chunk:20}") int rowsPerChunk) {
        this.rowsPerChunk = rowsPerChunk;
    }

    @Override
    public String supportedType() {
        return KnowledgeFileType.XLSX;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ParsedDocument parse(Path filePath) throws Exception {
        // headRowNumber(0)：不做表头映射，所有行（含首行）以列索引为 key 的原生 Map 返回
        List<Map<Integer, String>> rows = EasyExcel.read(filePath.toFile())
                .sheet(0)
                .headRowNumber(0)
                .doReadSync();
        List<ParsedBlock> blocks = new ArrayList<>();
        // 定位表头：首个非空行
        int headerIndex = -1;
        Map<Integer, String> header = null;
        for (int i = 0; i < rows.size(); i++) {
            if (!isBlankRow(rows.get(i))) {
                header = rows.get(i);
                headerIndex = i;
                break;
            }
        }
        if (header == null) {
            return new ParsedDocument(List.of(), 0, List.of("工作表为空，未提取到任何数据"));
        }
        StringBuilder current = new StringBuilder("表头: ").append(formatRow(header));
        int groupCount = 0;
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            Map<Integer, String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            if (groupCount >= rowsPerChunk) {
                blocks.add(new ParsedBlock(null, 0, current.toString().trim(), null));
                current.setLength(0);
                current.append("表头: ").append(formatRow(header));
                groupCount = 0;
            }
            current.append('\n').append(formatRow(row));
            groupCount++;
        }
        if (current.length() > 0) {
            blocks.add(new ParsedBlock(null, 0, current.toString().trim(), null));
        }
        int totalChars = blocks.stream().mapToInt(b -> b.text().length()).sum();
        return new ParsedDocument(blocks, totalChars, List.of());
    }

    /** 一行是否全空白 */
    private boolean isBlankRow(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        return row.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    /** 行按列索引排序后以 | 连接（保证列顺序稳定） */
    private String formatRow(Map<Integer, String> row) {
        if (row == null || row.isEmpty()) {
            return "";
        }
        return row.keySet().stream().sorted()
                .map(c -> {
                    String v = row.get(c);
                    return v == null ? "" : v.trim();
                })
                .collect(Collectors.joining(" | "));
    }
}
