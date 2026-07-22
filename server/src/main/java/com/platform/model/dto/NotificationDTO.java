package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String type;
    private String title;
    private String content;
    private Long relatedId;
    private Boolean isRead;
    private LocalDateTime createdAt;
    /** 当前用户是否可对此通知关联的记录进行评价（已完成 + 未评分） */
    private Boolean rateable;
    /** 通知的预期操作是否仍然有效（审批类：仍为待处理；评价类：同 rateable） */
    private Boolean actionable;
}
