package com.platform.model.dto;

import lombok.Data;


@Data
public class HelpRequestDTO {
    private Long userId;
    /** 是否为物业代发（管理员代为发布时设为 true） */
    private Boolean isProxy;
    private String title;
    private String description;
    private String category;
    private Boolean isUrgent;
    private String timeStart;
    private String timeEnd;
    private String location;
    private String rewardType;
    private String images;
}
