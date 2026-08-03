package com.platform.ai.agent;

/**
 * my_posts 工具参数 — 查询我发布的物品列表。
 *
 * @param postType 发布类型过滤：LEND(出借)/WANTED(求借)，空默认 LEND
 */
public record MyPostsParams(String postType) {
}
