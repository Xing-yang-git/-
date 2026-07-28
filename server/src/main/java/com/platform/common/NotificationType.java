package com.platform.common;

/**
 * 通知类型常量 — notifications.type 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 NOTIFICATION_TYPE 和
 * B端 admin/src/utils/constants.ts 的 NOTIFICATION_TYPE 保持一致。</p>
 */
public final class NotificationType {

    /** 工具类，禁止实例化 */
    private NotificationType() {}

    /** 借用申请通知 */
    public static final String BORROW_REQUEST = "borrow_request";
    /** 借用审批结果通知 */
    public static final String BORROW_RESULT = "borrow_result";
    /** 帮助申请通知 */
    public static final String HELP_APPLICATION = "help_application";
    /** 帮助处理结果通知 */
    public static final String HELP_RESULT = "help_result";
    /** 用户审核结果通知 */
    public static final String AUDIT_RESULT = "audit_result";
    /** 违规处理通知 */
    public static final String VIOLATION = "violation";
    /** 归还确认通知 */
    public static final String RETURN_CONFIRM = "return_confirm";
    /** 通用通知 */
    public static final String NOTIFICATION = "notification";
}
