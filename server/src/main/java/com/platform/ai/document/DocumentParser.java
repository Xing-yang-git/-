package com.platform.ai.document;

import java.nio.file.Path;

/**
 * 文档解析器接口 — 按文件类型把源文档解析为结构化文本块。
 *
 * <p>实现类通过 {@link #supportedType()} 声明支持的 {@link com.platform.common.KnowledgeFileType}，
 * 由 {@link DocumentParserRegistry} 按扩展名路由。新增文件格式只需新增一个实现类。</p>
 */
public interface DocumentParser {

    /**
     * 声明支持的文档类型（{@link com.platform.common.KnowledgeFileType} 取值）。
     *
     * @return 文件类型，如 "pdf" / "docx"
     */
    String supportedType();

    /**
     * 解析源文件为结构化文本块。
     *
     * @param filePath 已落盘的源文件路径
     * @return 解析结果（文本块 + 警告）
     * @throws Exception 解析失败（如加密文档、损坏文件），由导入编排捕获并标记文档失败
     */
    ParsedDocument parse(Path filePath) throws Exception;
}
