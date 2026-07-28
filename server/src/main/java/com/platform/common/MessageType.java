package com.platform.common;

/**
 * 消息类型常量 — messages.message_type 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 MESSAGE_TYPE 和
 * B端 admin/src/utils/constants.ts 的 MESSAGE_TYPE 保持一致。</p>
 */
public final class MessageType {

    /** 工具类，禁止实例化 */
    private MessageType() {}

    /** 普通文本消息 */
    public static final String TEXT = "text";
    /** 系统消息（如会话建立通知、撤回提示） */
    public static final String SYSTEM = "system";
}
