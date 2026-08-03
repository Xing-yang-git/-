package com.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识条目创建/更新请求 DTO。
 *
 * <p>B端知识库管理页提交，后端据此创建或更新 {@code knowledge_items} 条目并生成向量。</p>
 */
@Data
public class KnowledgeRequest {

    /** 所属小区 ID（普通 admin 强制用自身 tenant；super_admin 创建时必须指定） */
    private Long tenantId;

    /** 分类：rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南) */
    @NotBlank(message = "分类不能为空")
    private String category;

    /** 条目标题（如"装修施工时间规定"） */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 条目正文（检索与问答来源） */
    @NotBlank(message = "内容不能为空")
    private String content;

    /** 来源文档名（如《小区规章制度》） */
    private String source;

    /** 逗号分隔标签 */
    private String tags;

    /** 状态：online(启用)/offline(停用)，空则默认 online */
    private String status;
}
