package com.platform.common;

/**
 * 通用业务异常 — 业务校验失败或资源不存在时抛出，映射 HTTP 400。
 *
 * <p>业务代码禁止抛裸 {@link RuntimeException}，应使用本项目统一异常类：
 * <ul>
 *   <li>业务校验失败 / 资源不存在 → {@link BizException}（400）</li>
 *   <li>上游 AI 服务失败 → {@link AiGenerationException}（502）</li>
 *   <li>并发版本冲突 → {@link VersionConflictException}（409）</li>
 * </ul>
 */
public class BizException extends RuntimeException {

    /**
     * 使用描述消息创建业务异常。
     *
     * @param message 业务描述信息
     */
    public BizException(String message) {
        super(message);
    }

    /**
     * 使用描述消息和原始异常创建业务异常。
     *
     * @param message 业务描述信息
     * @param cause   原始异常
     */
    public BizException(String message, Throwable cause) {
        super(message, cause);
    }
}
