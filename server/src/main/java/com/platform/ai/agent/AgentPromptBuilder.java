package com.platform.ai.agent;

import com.platform.ai.search.KnowledgeHit;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 对话 Prompt 组装器 — 构建「小邻」助手的 System Prompt 与用户消息。
 *
 * <p>System Prompt 结构：身份 → 知识库上下文块（检索命中，按相关度排序）→ 回答铁则。
 * 引用来源由后端检索生成（非模型输出），prompt 强制"资料未覆盖必须明说"防幻觉。</p>
 */
@Component
public class AgentPromptBuilder {

    /** 对话专属系统提示词模板（知识库命中时） */
    private static final String SYSTEM_PROMPT_WITH_KB =
            "你是%s小区的智能助手「小邻」，帮助住户了解小区规章制度、物业服务、平台使用问题，" +
            "也能帮忙搜索物品、发起求助。\n\n" +
            "以下是知识库检索到的资料（按相关度排序，可能未覆盖全部）：\n%s\n\n" +
            "铁则：\n" +
            "- 回答只依据以上资料，资料未覆盖时明确说\"知识库暂无相关信息，建议联系物业或查看服务公告\"，禁止编造\n" +
            "- 引用资料时自然带出出处名称（如\"根据《%s》……\"）\n" +
            "- 口语化、简洁（不超过 120 字优先），不用 emoji，不出现住户真实姓名房号\n" +
            "- 用户打招呼或闲聊时正常寒暄，不强行套用资料";

    /** 对话专属系统提示词模板（无知识库命中 / 检索失败时） */
    private static final String SYSTEM_PROMPT_NO_KB =
            "你是%s小区的智能助手「小邻」，帮助住户了解小区规章制度、物业服务、平台使用问题，" +
            "也能帮忙搜索物品、发起求助。\n\n" +
            "铁则：\n" +
            "- 回答若涉及小区规则/服务/办事指南，知识库暂无相关信息时明确说\"知识库暂无相关信息，建议联系物业或查看服务公告\"，禁止编造\n" +
            "- 口语化、简洁（不超过 120 字优先），不用 emoji，不出现住户真实姓名房号\n" +
            "- 用户打招呼或闲聊时正常寒暄";

    /**
     * 构建对话 Prompt（知识问答场景 temperature 0.2）。
     *
     * @param tenantName 小区名称（用于助手自我介绍）
     * @param message    用户消息
     * @param hits       知识库检索命中（可为空）
     * @return 组装好的 Spring AI Prompt
     */
    public Prompt build(String tenantName, String message, List<KnowledgeHit> hits) {
        String system;
        if (hits != null && !hits.isEmpty()) {
            String kbBlock = formatKbBlock(hits);
            String firstSource = hits.get(0).source() != null ? hits.get(0).source() : "小区资料";
            system = String.format(SYSTEM_PROMPT_WITH_KB, tenantName, kbBlock, firstSource);
        } else {
            system = String.format(SYSTEM_PROMPT_NO_KB, tenantName);
        }

        return new Prompt(
                List.of(new SystemMessage(system), new UserMessage(message)),
                OpenAiChatOptions.builder()
                        .temperature(0.2)
                        // reasoning 模型思维链占用大，512 实测 content 为空（finish_reason=length），1024 预留足够空间
                        .maxTokens(1024)
                        .build());
    }

    /**
     * 将检索命中格式化为知识库上下文块（编号 + 来源 + 内容）。
     *
     * @param hits 知识命中列表
     * @return 上下文块文本
     */
    private String formatKbBlock(List<KnowledgeHit> hits) {
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (KnowledgeHit hit : hits) {
            String source = hit.source() != null ? hit.source() : "小区资料";
            sb.append("[").append(idx).append("] 来源：《").append(source)
                    .append("》 | 分类：").append(hit.category()).append("\n")
                    .append(hit.content()).append("\n");
            idx++;
        }
        return sb.toString();
    }
}
