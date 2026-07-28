package com.platform.common;

/**
 * 管理员操作类型常量 — operation_logs.action 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 OPERATION_ACTION 和
 * B端 admin/src/utils/constants.ts 的 OPERATION_ACTION 保持一致。</p>
 */
public final class OperationAction {

    /** 工具类，禁止实例化 */
    private OperationAction() {}

    /** 审核通过用户认证 */
    public static final String APPROVE_USER = "approve_user";
    /** 驳回用户认证 */
    public static final String REJECT_USER = "reject_user";
    /** 下架内容（闲置物品或求助信息） */
    public static final String REMOVE_CONTENT = "remove_content";
    /** 代发闲置物品 */
    public static final String PROXY_PUBLISH_IDLE = "proxy_publish_idle";
    /** 代发求助信息 */
    public static final String PROXY_PUBLISH_HELP = "proxy_publish_help";
    /** 创建管理员账号 */
    public static final String CREATE_ADMIN = "create_admin";
    /** 删除管理员账号 */
    public static final String DELETE_ADMIN = "delete_admin";
}
