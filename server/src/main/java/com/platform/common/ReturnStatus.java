package com.platform.common;

/**
 * 归还状态常量 — borrow_requests.return_status 字段的唯一合法取值。
 * 与 C端 miniprogram/utils/constants.js 的 RETURN_STATUS 保持一致。
 */
public final class ReturnStatus {
    private ReturnStatus() {}

    /** 按时归还 */
    public static final String ON_TIME = "ontime";
    /** 逾期归还 */
    public static final String DELAYED = "delayed";
    /** 未归还 */
    public static final String NOT_RETURNED = "not_returned";
}
