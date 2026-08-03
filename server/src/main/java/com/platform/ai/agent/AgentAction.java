package com.platform.ai.agent;

import java.util.Map;

/**
 * Agent 动作卡片 — 写操作（发布/发起）需用户确认后执行，不自动落库。
 *
 * <p>由 IntentRouter 从模型返回的 JSON 意图解析生成，前端渲染确认卡片，
 * 用户点击「去发布」后携带 params 跳转发布页预填。</p>
 *
 * @param type   动作类型：publish_help / publish_idle / publish_wanted
 * @param label  动作按钮文案（如"帮您发起求助"）
 * @param params 预填参数（title/description/category 等）
 */
public record AgentAction(String type, String label, Map<String, Object> params) {
}
