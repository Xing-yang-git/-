package com.platform.common;

/**
 * 消息送达状态常量 — messages.status 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 MESSAGE_STATUS 和
 * B端 admin/src/utils/constants.ts 的 MESSAGE_STATUS 保持一致。</p>
 */
public final class MessageStatus {

    /** 工具类，禁止实例化 */
    private MessageStatus() {}

    /** 已发送（服务端已接收，待送达） */
    public static final String SENT = "sent";
    /** 已送达（消息已推送到接收方） */
    public static final String DELIVERED = "delivered";
    /** 已读（接收方已查看） */
    public static final String READ = "read";
}
