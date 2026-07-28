package com.platform.common;

/**
 * 损坏类型常量 — borrow_requests.damage_type 字段的唯一合法取值。
 * 与 C端 miniprogram/utils/constants.js 的 DAMAGE_TYPE 保持一致。
 */
public final class DamageType {
    private DamageType() {}

    /** 正常损耗 */
    public static final String NORMAL = "normal";
    /** 非正常损坏（数据库值保持 severe 是历史原因） */
    public static final String ABNORMAL = "severe";
    /** 完全损坏 */
    public static final String BROKEN = "broken";
}
