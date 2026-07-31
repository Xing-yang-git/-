package com.platform.common;

/**
 * AI 生成异常 — 调用智谱 AI API 失败时抛出。
 *
 * <p>在 {@link GlobalExceptionHandler} 中统一处理为 502 Bad Gateway，
 * 表示上游 AI 服务不可用或返回异常结果。</p>
 */
public class AiGenerationException extends RuntimeException {

    /**
     * 使用描述消息创建 AI 生成异常。
     *
     * @param message 异常描述信息
     */
    public AiGenerationException(String message) {
        super(message);
    }

    /**
     * 使用描述消息和原始异常创建 AI 生成异常。
     *
     * @param message 异常描述信息
     * @param cause   原始异常
     */
    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
