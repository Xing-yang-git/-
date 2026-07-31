package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * B端内容管理统一 DTO，覆盖闲置与求助两类内容在各管理 tab 下的展示字段。
 * 一个 DTO 承载：待发布、展示中、进行中、已完成、待审批、已驳回、已下架等所有状态的视图数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentItemDTO {

    /** 内容主键 ID */
    private Long id;

    /** 内容大类，有效值："idle"（闲置）/ "help"（求助） */
    private String type;

    /** 发布类型，有效值："LEND"（借出）/ "WANTED"（求借）/ "HELP"（求助） */
    private String postType;

    /** 内容标题 */
    private String title;

    /** 内容描述文本 */
    private String description;

    /** 内容配图 URL 列表，可能为空 */
    private List<String> images;

    /** 内容分类标签 */
    private String category;

    /** 价格，仅闲置类型使用 */
    private BigDecimal price;

    /** 物品成色，仅闲置类型使用 */
    private String condition;

    /** 发布者昵称 */
    private String publisherName;

    /** 发布者房号，格式示例："3栋2单元1502号(业主)" */
    private String publisherRoom;

    /** 前端展示状态，有效值："展示中" / "进行中" / "已完成" / "已下架" */
    private String displayStatus;

    /** 数据库实际存储的原始状态值 */
    private String rawStatus;

    /** 是否为物业代发 */
    private Boolean isProxy;

    /** 是否为紧急求助，仅求助类型使用 */
    private Boolean isUrgent;

    /** 内容创建时间 */
    private LocalDateTime createdAt;

    /** 对方昵称，用于进行中/已完成场景 */
    private String peerName;

    /** 对方房号，用于进行中/已完成场景 */
    private String peerRoom;

    /** 对方评分，用于进行中/已完成场景 */
    private Double peerRating;

    /** 开始时间：求助为期望开始时间，闲置为借用起始时间 */
    private LocalDateTime timeStart;

    /** 结束时间：求助为期望结束时间，闲置为借用结束时间 */
    private LocalDateTime timeEnd;

    /** 最长借出/借入天数，仅闲置类型使用 */
    private Integer maxDuration;

    /** 借用时长单位，仅闲置类型使用 */
    private String durationUnit;

    /** 借入/帮忙申请时间，时间线节点之一 */
    private LocalDateTime applyAt;

    /** 同意借出/帮忙的时间，时间线节点之一 */
    private LocalDateTime approveAt;

    /** 互助结束确认时间，时间线节点之一 */
    private LocalDateTime completeAt;

    /** 发布者收到的星级评价文字（求助方/借出方评价），已废弃，仅用于旧数据展示 */
    private String publisherRatingStars;

    /** 发布者收到的评分分数（求助方/借出方评分） */
    private Double publisherRatingScore;

    /** 发布者的互助感想评价文字（求助方/借出方对本次互助的评价） */
    private String publisherRatingComment;

    /** 对方收到的星级评价文字（相助方/借入方评价），已废弃，仅用于旧数据展示 */
    private String peerRatingStars;

    /** 对方收到的评分分数（相助方/借入方评分） */
    private Double peerRatingScore;

    /** 对方的互助感想评价文字（相助方/借入方对本次互助的评价） */
    private String peerRatingComment;

    /** 审批人姓名，管理员审核后填充，NULL 表示尚未人工审核 */
    private String approverName;

    /** 申请人姓名（发布者），与 publisherName 相同，语义化别名 */
    private String applicantName;

    /** 楼栋名称，用于筛选条件 */
    private String building;

    /** AI 审核状态，引用 {@link com.platform.common.ModerationStatus}，有效值：GREEN（通过）/ YELLOW（待复核）/ RED（驳回）/ REVIEWED（已人工复核） */
    private String moderationStatus;

    /** 审核管理员姓名，NULL 表示由 AI 自动处理，非 NULL 表示经人工审核 */
    private String reviewedByName;

    /** 统一下架原因，闲置与求助共用 */
    private String delistReason;

    /** 更新时间，记录最后修改时刻 */
    private LocalDateTime updatedAt;
}
