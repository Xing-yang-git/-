package com.platform.ai.document.parser;

import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.ai.document.TextCharsetDetector;
import com.platform.common.KnowledgeFileType;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 纯文本解析器（TXT）— 整篇作为一个块，sectionTitle 取文件名。
 *
 * <p>字符集由 {@link TextCharsetDetector} 检测（UTF-8 / GBK 中文文档）。</p>
 * <p>【暂时禁用】TXT 类型暂不支持（Controller 已拦截上传），恢复时取消下方 {@code @Component} 注释即可，
 * 类保留以便单元测试直接构造。</p>
 */
// @Component
public class TextParser implements DocumentParser {

    @Override
    public String supportedType() {
        return KnowledgeFileType.TXT;
    }

    @Override
    public ParsedDocument parse(Path filePath) throws Exception {
        byte[] bytes = Files.readAllBytes(filePath);
        Charset charset = TextCharsetDetector.detect(bytes);
        String text = new String(bytes, charset).trim();
        if (text.isEmpty()) {
            return new ParsedDocument(List.of(), 0, List.of());
        }
        String section = baseNameWithoutExt(filePath);
        return new ParsedDocument(List.of(new ParsedBlock(section, 0, text, null)), text.length(), List.of());
    }

    /** 取文件名（去扩展名）作为章节名 */
    private String baseNameWithoutExt(Path filePath) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
