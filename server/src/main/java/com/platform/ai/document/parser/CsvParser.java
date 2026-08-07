package com.platform.ai.document.parser;

import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.ai.document.TextCharsetDetector;
import com.platform.common.KnowledgeFileType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV 解析器 — 首条记录作表头，之后每 rows-per-chunk 行一组生成一个块。
 *
 * <p>字符集经 {@link TextCharsetDetector} 检测；commons-csv 自动剥 BOM。块内容 = 表头 + 数据行。</p>
 * <p>【暂时禁用】CSV 类型暂不支持（Controller 已拦截上传），恢复时取消下方 {@code @Component} 注释即可，
 * 类保留以便单元测试直接构造。</p>
 */
// @Component
public class CsvParser implements DocumentParser {

    /** 每组数据行数 */
    private final int rowsPerChunk;

    /**
     * 构造器注入。
     *
     * @param rowsPerChunk 每组数据行数（ai.doc.rows-per-chunk）
     */
    public CsvParser(@Value("${ai.doc.rows-per-chunk:20}") int rowsPerChunk) {
        this.rowsPerChunk = rowsPerChunk;
    }

    @Override
    public String supportedType() {
        return KnowledgeFileType.CSV;
    }

    @Override
    public ParsedDocument parse(Path filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(filePath);
        Charset charset = TextCharsetDetector.detect(bytes);
        List<ParsedBlock> blocks = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(bytes), charset);
             CSVParser parser = new CSVParser(reader, CSVFormat.RFC4180)) {
            List<CSVRecord> records = parser.getRecords();
            if (records.isEmpty()) {
                return new ParsedDocument(List.of(), 0, List.of("CSV 为空，未提取到任何数据"));
            }
            CSVRecord header = records.get(0);
            StringBuilder current = new StringBuilder("表头: ").append(formatRecord(header));
            int groupCount = 0;
            for (int i = 1; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                if (isBlankRecord(record)) {
                    continue;
                }
                if (groupCount >= rowsPerChunk) {
                    blocks.add(new ParsedBlock(null, 0, current.toString().trim(), null));
                    current.setLength(0);
                    current.append("表头: ").append(formatRecord(header));
                    groupCount = 0;
                }
                current.append('\n').append(formatRecord(record));
                groupCount++;
            }
            if (current.length() > 0) {
                blocks.add(new ParsedBlock(null, 0, current.toString().trim(), null));
            }
        }
        int totalChars = blocks.stream().mapToInt(b -> b.text().length()).sum();
        return new ParsedDocument(blocks, totalChars, List.of());
    }

    /** 一条记录是否全空白 */
    private boolean isBlankRecord(CSVRecord record) {
        for (int i = 0; i < record.size(); i++) {
            String v = record.get(i);
            if (v != null && !v.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** 记录字段以 | 连接 */
    private String formatRecord(CSVRecord record) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < record.size(); i++) {
            String v = record.get(i);
            values.add(v == null ? "" : v.trim());
        }
        return String.join(" | ", values);
    }
}
