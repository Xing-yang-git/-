package com.platform.ai.agent;

/**
 * search_items 工具参数 — 搜索闲置物品。
 *
 * @param keyword  搜索关键词（必填）
 * @param postType 发布类型过滤：LEND(出借)/WANTED(求借)/HELP(求助)，空默认 LEND
 */
public record SearchParams(String keyword, String postType) {
}
