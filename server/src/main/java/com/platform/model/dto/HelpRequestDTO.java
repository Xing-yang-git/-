package com.platform.model.dto;

import lombok.Data;


/**
 * 求助发布/编辑请求 DTO。
 * 客户端提交求助信息时使用的请求体，包含求助帖子的全部可编辑字段。
 */
@Data
public class HelpRequestDTO {

    /** 发布者用户 ID，外键关联 users 表 */
    private Long userId;

    /** 是否为物业代发，管理员代为发布时设为 true */
    private Boolean isProxy;

    /** 求助标题 */
    private String title;

    /** 求助详细描述 */
    private String description;

    /** 求助分类标签 */
    private String category;

    /** 是否为紧急求助 */
    private Boolean isUrgent;

    /** 期望开始时间，格式为 "yyyy-MM-dd HH:mm" */
    private String timeStart;

    /** 期望结束时间，格式为 "yyyy-MM-dd HH:mm" */
    private String timeEnd;

    /** 求助地点/位置描述 */
    private String location;


    /** 配图 URL，可能为 JSON 数组字符串 */
    private String images;
}
