package com.platform.model.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识条目列表响应 DTO（B端知识库管理页）。
 *
 * <p>不含 embedding 向量（体积大且前端不需要），仅返回展示所需字段。</p>
 */
@Data
@Builder
public class KnowledgeItemDTO {

    /** 知识条目 ID */
    private Long id;

    /** 所属小区 ID */
    private Long tenantId;

    /** 分类：rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南) */
    private String category;

    /** 条目标题 */
    private String title;

    /** 条目正文 */
    private String content;

    /** 来源文档名 */
    private String source;

    /** 逗号分隔标签 */
    private String tags;

    /** 状态：online(启用)/offline(停用) */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
