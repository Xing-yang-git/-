package com.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 文案优化请求 DTO。
 *
 * <p>前端提交当前场景模式、发布类型或评价角色、标题、描述等背景信息，
 * 后端根据 mode 选择对应的 AI 处理逻辑（评价生成、未来可扩展标题润色等）。</p>
 */
@Data
public class PolishRequest {

    /** 功能模式：feedback（互助感想生成），预留未来扩展 */
    @NotBlank(message = "mode 不能为空")
    private String mode;

    /** 角色标识：borrow（我借入）/ lend（我借出）/ helpReq（我求助）/ helpPro（我帮忙） */
    @NotBlank(message = "role 不能为空")
    private String role;

    /** 物品标题或求助标题 */
    @NotBlank(message = "itemTitle 不能为空")
    private String itemTitle;

    /** 补充背景描述：归还情况、物品状况、用户已有草稿等 */
    private String description;
}
