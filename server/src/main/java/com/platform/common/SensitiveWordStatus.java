package com.platform.common;

/**
 * 敏感词状态枚举 — sensitive_word.status 字段的唯一合法取值。
 *
 * <p>持久化为字符串（ENABLED/DISABLED），与表默认值 'ENABLED' 及 B端契约严格一致；
 * 仅启用词参与对话输入前置过滤匹配，停用词不生效。</p>
 */
public enum SensitiveWordStatus {

    /** 启用（参与匹配拦截） */
    ENABLED,

    /** 停用（不参与匹配拦截，保留词库配置） */
    DISABLED
}
