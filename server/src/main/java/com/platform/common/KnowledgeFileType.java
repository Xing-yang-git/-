package com.platform.common;

import java.util.Map;
import java.util.Set;

/**
 * 知识库源文档类型常量 — knowledge_documents.file_type 的唯一合法取值。
 * <p>与 B端上传白名单保持一致；新增格式需同步解析器注册表 {@code DocumentParserRegistry}。</p>
 */
public final class KnowledgeFileType {
    /** 工具类，禁止实例化 */
    private KnowledgeFileType() {
    }

    /** Markdown */
    public static final String MD = "md";
    /** 纯文本 */
    public static final String TXT = "txt";
    /** PDF */
    public static final String PDF = "pdf";
    /** Word */
    public static final String DOCX = "docx";
    /** Excel */
    public static final String XLSX = "xlsx";
    /** CSV */
    public static final String CSV = "csv";

    /** 各类型单文件大小上限（字节），防止超大文件拖垮解析/OCR */
    private static final long MB = 1024L * 1024L;
    /** 各类型单文件大小上限映射 */
    public static final Map<String, Long> MAX_SIZE_BYTES = Map.of(
            MD, 5L * MB,
            TXT, 5L * MB,
            CSV, 5L * MB,
            DOCX, 10L * MB,
            PDF, 30L * MB,   // 扫描版图片 PDF 体积大，统一放宽到 30MB
            XLSX, 10L * MB
    );

    /** 允许的文件类型集合 */
    public static final Set<String> ALLOWED = MAX_SIZE_BYTES.keySet();

    /**
     * 由文件名解析文件类型（小写扩展名），不在白名单返回 null。
     *
     * @param fileName 原始文件名（含扩展名）
     * @return 文件类型，或 null
     */
    public static String fromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        return ALLOWED.contains(ext) ? ext : null;
    }
}
