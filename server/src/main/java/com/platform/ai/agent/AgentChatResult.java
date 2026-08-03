package com.platform.ai.agent;

import com.platform.ai.search.KnowledgeHit;

import java.util.List;

/**
 * Agent 对话结果 — 回复文本 + 引用来源。
 *
 * @param reply    AI 生成回复文本
 * @param sources  知识库引用来源（后端检索结果，非模型输出，防幻觉）
 */
public record AgentChatResult(String reply, List<KnowledgeHit> sources) {
}
