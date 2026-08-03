package com.platform.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Agent 对话流式事件（SSE）。
 *
 * <p>事件类型：
 * <ul>
 *   <li>start — 会话开始（含本次消息 id）</li>
 *   <li>answer — 回复文本分块（伪流式逐字/逐句播放）</li>
 *   <li>sources — 引用来源列表（后端检索结果）</li>
 *   <li>action — 动作卡片（Step 4 写操作，需用户确认）</li>
 *   <li>end — 会话结束</li>
 *   <li>error — 错误信息</li>
 * </ul>
 */
@Data
@AllArgsConstructor
public class AgentStreamEvent {

    /** 事件类型：start/answer/sources/action/end/error */
    private String type;

    /** 事件数据：answer 为文本分块，sources 为引用列表，action 为动作卡片 */
    private Object data;
}
