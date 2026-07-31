package com.platform.common;

/**
 * AI 内容审核状态常量 — idle_items.moderation_status 和 help_requests.moderation_status 字段的唯一合法取值。
 *
 * <p>审核状态流转：
 * <ul>
 *   <li>内容发布后设为 {@link #PENDING}（待 AI 审核）</li>
 *   <li>AI 审核完成 → {@link #GREEN}（通过，自动上线）/ {@link #YELLOW}（待人工复核）/ {@link #RED}（驳回，自动下架）</li>
 *   <li>管理员处理 YELLOW → {@link #REVIEWED}（已人工复核）</li>
 *   <li>NULL 表示非审核流程的下架（用户自行下架、管理员在其他 tab 下架等），不参与 moderation 筛选</li>
 * </ul>
 */
public final class ModerationStatus {

    private ModerationStatus() {
        // 工具类，禁止实例化
    }

    /** 待 AI 审核（内容发布后的初始状态，等待异步审核完成） */
    public static final String PENDING = "pending";

    /** AI 审核通过（内容合规，自动上线展示） */
    public static final String GREEN = "green";

    /** 待人工复核（AI 判定疑似不合规，需管理员确认） */
    public static final String YELLOW = "yellow";

    /** AI 审核驳回（AI 确认违规，内容自动下架并通知用户） */
    public static final String RED = "red";

    /** 已人工复核（管理员已手动处理过该内容） */
    public static final String REVIEWED = "reviewed";
}
