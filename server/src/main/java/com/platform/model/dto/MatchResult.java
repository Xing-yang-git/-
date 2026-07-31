package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 语义匹配结果 DTO — 用于供需匹配中表示一个 LEND 物品与 WANTED 需求的相似度。
 *
 * <p>距离越小表示语义越相似（pgvector 余弦距离 ≤ 1，0 表示完全相同）。</p>
 */
@Data
@AllArgsConstructor
public class MatchResult {

    /** 匹配到的出借物品 ID */
    private Long lendItemId;

    /** 匹配到的出借物品标题 */
    private String lendTitle;

    /** 出借方用户 ID */
    private Long lenderUserId;

    /** 余弦距离（越小越相似），来自 pgvector {@code <=>} 运算符 */
    private Double distance;
}
