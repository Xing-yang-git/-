package com.platform.common;

/**
 * 记忆压缩段状态常量 — agent_memory_segments.status 字段的唯一合法取值。
 *
 * <p>压缩生命周期：SUCCESS(压缩成功，标题/摘要/向量就绪) / RETRY(压缩失败，标题兜底/摘要为空，会话结束时补压)。</p>
 */
public final class MemorySegmentStatus {

    /** 工具类，禁止实例化 */
    private MemorySegmentStatus() {
    }

    /** 压缩成功（标题/摘要/向量完整） */
    public static final String SUCCESS = "SUCCESS";

    /** 压缩失败待补压（LLM 失败或解析失败降级，标题用兜底、摘要为空；由 compressRetry 会话结束时补压） */
    public static final String RETRY = "RETRY";
}
