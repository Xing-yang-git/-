package com.platform.common;

/**
 * 取货方式常量 — idle_items.pickup_method 字段的唯一合法取值。
 *
 * <p>与 C端 miniprogram/utils/constants.js 的 PICKUP_METHOD 和
 * B端 admin/src/utils/constants.ts 的 PICKUP_METHOD 保持一致。</p>
 */
public final class PickupMethod {

    /** 工具类，禁止实例化 */
    private PickupMethod() {}

    /** 自取（借用方上门取货） */
    public static final String SELF_PICKUP = "self_pickup";
    /** 快递（借出方邮寄发货） */
    public static final String EXPRESS = "express";
}
