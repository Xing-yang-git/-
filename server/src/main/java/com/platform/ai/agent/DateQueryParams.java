package com.platform.ai.agent;

/**
 * query_date 工具参数 — 查询日期和星期。
 *
 * @param expression 相对日期描述（如「明天」「前天」「下周三」「3天后」）
 */
public record DateQueryParams(String expression) {
}
