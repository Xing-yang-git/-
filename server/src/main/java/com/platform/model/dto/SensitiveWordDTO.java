package com.platform.model.dto;

import com.platform.common.SensitiveWordStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 敏感词数据传输对象 — 管理端敏感词 CRUD 的请求/响应契约（不直接暴露 Entity）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveWordDTO {

    /** 敏感词 ID（新增时为空，响应时填充） */
    private Long id;

    /** 敏感词原文（必填，新增/编辑去重） */
    @NotBlank(message = "敏感词不能为空")
    private String word;

    /** 状态：ENABLED(启用)/DISABLED(停用)，新增时缺省为 ENABLED */
    private SensitiveWordStatus status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
