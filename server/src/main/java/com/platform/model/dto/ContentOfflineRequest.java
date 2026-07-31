package com.platform.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class ContentOfflineRequest {
    /** 内容类型："idle" 或 "help" */
    private String targetType;
    /** 预设违规原因列表 */
    private List<String> reasons;
    /** 自定义补充原因文本 */
    private String customReason;
    /** 是否来自审核 tab 的驳回操作（true：审核驳回 → moderationStatus 设为 ModerationStatus.RED；false/不传：其他 tab 下架 → 不动 moderationStatus） */
    private boolean fromModeration;
    /** 乐观锁：管理员打开弹窗时的 updatedAt，用于检测并发修改。ISO 格式字符串，可选（不传则跳过版本检查） */
    private String updatedAt;
}
