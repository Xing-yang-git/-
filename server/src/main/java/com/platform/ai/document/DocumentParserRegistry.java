package com.platform.ai.document;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文档解析器注册表 — 按文件类型路由到对应解析器。
 *
 * <p>所有 {@link DocumentParser} Spring Bean 自动注入；新增格式无需改动本类。</p>
 */
@Component
public class DocumentParserRegistry {

    /** 文件类型 → 解析器映射 */
    private final Map<String, DocumentParser> parserMap;

    /**
     * 收集容器内全部解析器 Bean 构建映射。
     *
     * @param parserList 全部 DocumentParser Bean
     */
    public DocumentParserRegistry(List<DocumentParser> parserList) {
        this.parserMap = parserList.stream()
                .collect(Collectors.toUnmodifiableMap(DocumentParser::supportedType, Function.identity()));
    }

    /**
     * 获取指定文件类型的解析器。
     *
     * @param fileType 文件类型（{@link com.platform.common.KnowledgeFileType} 取值）
     * @return 对应解析器
     * @throws IllegalArgumentException 未注册该类型时抛出
     */
    public DocumentParser get(String fileType) {
        DocumentParser parser = parserMap.get(fileType);
        if (parser == null) {
            throw new IllegalArgumentException("未注册的文件类型解析器: " + fileType);
        }
        return parser;
    }
}
