package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 文案优化响应 DTO。
 *
 * <p>包含 AI 生成的文本内容，前端直接填入对应的输入框。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolishResponse {

    /** AI 生成的评价文本（mode=feedback 时返回） */
    private String feedback;
}
