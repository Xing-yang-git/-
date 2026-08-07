package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库源文档 DTO — B端文档管理列表响应。
 *
 * <p>只暴露前端实际使用的字段（管理页仅展示文件名/分类/状态/更新时间，不关心向量/标签/切片数）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDTO {

    /** 源文档 ID */
    private Long id;

    /** 分类：rules(规章制度)/service(服务手册)/help(平台帮助)/guide(办事指南) */
    private String category;

    /** 原始文件名（含扩展名） */
    private String fileName;

    /** 文件类型：md/txt/pdf/docx/xlsx/csv */
    private String fileType;

    /** 展示来源名（默认文件名去扩展名，问答引用出处） */
    private String source;

    /** 处理状态：parsing(解析中)/ready(就绪)/failed(失败可重试) */
    private String status;

    /** 失败原因 / 部分内容未处理警告 */
    private String errorMessage;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
