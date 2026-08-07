package com.platform.common;

/**
 * 知识库源文档处理状态常量 — knowledge_documents.status 的唯一合法取值。
 * <p>状态机：parsing(解析中) → ready(就绪) / failed(失败可重试)；应用启动时会把卡死的 parsing 重置为 failed。</p>
 */
public final class DocumentStatus {
    /** 工具类，禁止实例化 */
    private DocumentStatus() {
    }

    /** 解析中（异步任务进行中） */
    public static final String PARSING = "parsing";
    /** 就绪（切片已嵌入入库） */
    public static final String READY = "ready";
    /** 失败（可重试） */
    public static final String FAILED = "failed";
}
