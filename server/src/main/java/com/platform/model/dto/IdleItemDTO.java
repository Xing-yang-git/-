package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C端闲置详情响应 DTO。
 * 包含闲置物品的完整信息、发布者信息、当前用户的借用申请状态以及发布者的历史互助统计数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdleItemDTO {

    /** 闲置物品主键 ID */
    private Long id;

    /** 发布者用户 ID，外键关联 users 表 */
    private Long userId;

    /** 发布者昵称 */
    private String userName;

    /** 发布者房号 */
    private String userRoom;

    /** 发布类型，有效值："LEND"（借出）/ "WANTED"（求借） */
    private String postType;

    /** 闲置物品标题 */
    private String title;

    /** 闲置物品详细描述 */
    private String description;

    /** 闲置物品分类标签 */
    private String category;

    /** 物品成色/新旧程度 */
    private String condition;

    /** 物品价格 */
    private BigDecimal price;

    /** 物品配图 URL，可能为 JSON 数组字符串 */
    private String images;

    /** 最长借出/借入天数 */
    private Integer maxDuration;

    /** 借用时长单位 */
    private String durationUnit;

    /** 取货方式 */
    private String pickupMethod;

    /** 物品状态，有效值："online"（在线）/ "draft"（草稿）/ "offline"（已下架）/ "completed"（已完成） */
    private String status;

    /** 统一下架原因 */
    private String delistReason;

    /** 是否为物业代发 */
    private Boolean isProxy;

    /** 帖子创建时间 */
    private LocalDateTime createdAt;

    /** 发布者评分 */
    private Double rating;

    /** 发布者累计借出次数 */
    private Long lendCount;

    /** 发布者归还率，格式为百分比字符串 */
    private String returnRate;

    /** 发布者累计借入次数 */
    private Long borrowCount;

    /** 发布者累计帮助他人次数 */
    private Long helpCount;

    /** 发布者累计被帮助次数 */
    private Long helpedCount;

    /** 当前用户对该物品的借用申请状态，NULL 表示未申请，有效值："pending"（待审核）/ "approved"（已同意）/ "returned"（已归还） */
    private String userBorrowStatus;
}
