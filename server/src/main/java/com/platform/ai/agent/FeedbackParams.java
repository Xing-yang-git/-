package com.platform.ai.agent;

/**
 * generate_feedback 工具参数 — 生成互助感想评价文本。
 *
 * @param role        角色：borrow(借入)/lend(借出)/helpReq(求助)/helpPro(帮忙)
 * @param itemTitle   物品标题或求助标题
 * @param description 补充背景（归还情况、物品状况、用户草稿等）
 */
public record FeedbackParams(String role, String itemTitle, String description) {
}
