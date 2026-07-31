package com.platform.ai.moderation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 内容审核结果 DTO。
 *
 * <p>封装单次审核（单张图片或文本）的输出结果，
 * 供 {@link ModerationService} 汇总多维度审核后进行三级分流决策。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationResult {

    /** 审核等级：green（放行）/ yellow（待人工复核）/ red（驳回） */
    private String level;

    /** 违规原因简述（green 时为空字符串） */
    private String reason;
}
