package com.platform.common;

/**
 * 业务状态常量类 — 集中管理散落在各 Service/Entity 中的状态字符串字面量。
 *
 * <p>字符串值与数据库存储值、前端契约严格一致，禁止修改。
 * 同一常量可能被多个业务域复用（例如 pending 同时用于借用申请、帮助申请与用户审核），
 * 按主要业务域分组说明如下：
 * <ul>
 *   <li>内容状态（IdleItem/HelpRequest.status）：online（展示中）/ offline（已下架）</li>
 *   <li>借用流转（BorrowRequest.status）：pending（待审批）→ approved（已同意）/ rejected（已拒绝）→ returned（已归还）</li>
 *   <li>帮助流转（HelpApplication.status）：pending（待审批）→ approved（已同意）/ rejected（已拒绝）→ completed（已完成）</li>
 *   <li>用户审核（User.authStatus）：pending（待审核）→ approved（已通过）/ rejected（已驳回）</li>
 *   <li>账号/记录状态：normal（正常，物品成色默认值）/ banned（已封禁）/ active（进行中，历史借用记录）</li>
 * </ul>
 *
 * <p>注意：borrowing/helping 等状态值因业务域耦合较深暂未纳入本类，仍以字面量形式存在。</p>
 */
public final class BizStatus {

    /** 工具类，禁止实例化 */
    private BizStatus() {
    }

    // ==================== 通用流转状态（借用 / 帮助 / 用户审核共用） ====================

    /** 待审批 / 待审核 */
    public static final String PENDING = "pending";

    /** 已同意 / 审核通过 */
    public static final String APPROVED = "approved";

    /** 已拒绝 / 审核驳回 */
    public static final String REJECTED = "rejected";

    /** 已归还（借用流转终态） */
    public static final String RETURNED = "returned";

    /** 已完成（帮助流转终态 / 内容完成态） */
    public static final String COMPLETED = "completed";

    /** 已预订（闲置物品已被锁定 / 帮助已有人接单） */
    public static final String RESERVED = "reserved";

    // ==================== 内容状态（闲置物品 / 求助信息） ====================

    /** 上架展示中 */
    public static final String ONLINE = "online";

    /** 已下架 */
    public static final String OFFLINE = "offline";

    // ==================== 账号 / 记录状态 ====================

    /** 正常（物品成色 condition 默认值） */
    public static final String NORMAL = "normal";

    /** 已封禁（用户 authStatus） */
    public static final String BANNED = "banned";

    /** 进行中（历史借用记录 status） */
    public static final String ACTIVE = "active";

    // ==================== 用户注册 ====================

    /** 注册中（用户已微信登录但尚未完成手机号绑定/实名） */
    public static final String REGISTERING = "registering";

    // ==================== 软删除 ====================

    /** 已删除（软删除标记，记录保留但不可见） */
    public static final String DELETED = "deleted";
}
