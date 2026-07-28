package com.platform.common;

/**
 * 借出时长单位常量 — idle_items.duration_unit 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 DURATION_UNIT 和
 * B端 admin/src/utils/constants.ts 的 DURATION_UNIT 保持一致。</p>
 */
public final class DurationUnit {

    /** 工具类，禁止实例化 */
    private DurationUnit() {}

    /** 按天计算 */
    public static final String DAY = "day";
    /** 按周计算 */
    public static final String WEEK = "week";
    /** 按月计算 */
    public static final String MONTH = "month";
}
