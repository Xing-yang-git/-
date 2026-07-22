package com.platform.common;

/**
 * 发布类型常量类 — 区分平台上的三种发布内容。
 *
 * <p>字符串值与数据库存储值、前端契约严格一致，禁止修改：
 * <ul>
 *   <li>LEND — 出借：用户发布自己的闲置物品供他人借用</li>
 *   <li>WANTED — 求借：用户发布想借入的物品需求</li>
 *   <li>HELP — 求助：用户发布互助请求（对应 HelpRequest 域）</li>
 * </ul>
 */
public final class PostType {

    /** 工具类，禁止实例化 */
    private PostType() {
    }

    /** 出借 — 发布闲置物品供他人借用 */
    public static final String LEND = "LEND";

    /** 求借 — 发布想借入的物品需求 */
    public static final String WANTED = "WANTED";

    /** 求助 — 发布互助请求 */
    public static final String HELP = "HELP";
}
