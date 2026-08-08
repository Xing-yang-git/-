package com.platform.ai.agent;

import com.platform.ai.common.PromptRepository;
import com.platform.common.AgentMessageRole;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 对话 Prompt 组装器 — 构建「小邻」助手的 System Prompt 与用户消息。
 *
 * <p>System Prompt 从提示词文件 {@code prompts/agent/system.md} 读取（key {@code agent.system}），
 * 运行时替换占位符：{@code {小区名}} → 小区名称、{@code {历史记忆}} → 记忆上下文变量（null/「无」时渲染「无」）。
 * 知识库资料不再拼进 System Prompt——检索已工具化（search_knowledge），模型按决策路由自主调用。
 * 读工具（search_items、search_knowledge 等）由 AgentService 注册 ToolCallback 自动暴露，无需在此描述。</p>
 */
@Component
public class AgentPromptBuilder {

    /** 提示词仓库（启动时已加载全部提示词文件到内存） */
    private final PromptRepository promptRepository;

    /**
     * 构造器注入。
     *
     * @param promptRepository 提示词仓库（key {@code agent.system} 对应的 System Prompt 模板）
     */
    public AgentPromptBuilder(PromptRepository promptRepository) {
        this.promptRepository = promptRepository;
    }

    /**
     * 构建对话消息列表（无记忆上下文版，{@code {历史记忆}} 固定渲染「无」）。
     *
     * @param tenantName 小区名称（替换 {@code {小区名}} 占位符）
     * @param message    用户消息
     * @param history    多轮历史（Redis 热会话，可为空）
     * @return system + 历史 + user 消息列表
     */
    public List<Message> buildMessages(String tenantName, String message,
                                       List<AgentSession.AgentMessageItem> history) {
        return buildMessages(tenantName, message, history, null);
    }

    /**
     * 构建对话消息列表（system + 历史 + user），模型 options 由 AgentService 组装（含工具注册）。
     *
     * <p>记忆注入：{@code {历史记忆}} 渲染 = memoryText 非空则其值、否则「无」；
     * 由 AgentService 按次实时检索注入，本组件不做检索。</p>
     *
     * @param tenantName 小区名称（替换 {@code {小区名}} 占位符）
     * @param message    用户消息
     * @param history    多轮历史（Redis 热会话，可为空）
     * @param memoryText 当前消息实时检索的记忆摘要文本（null 或「无」时渲染「无」）
     * @return system + 历史 + user 消息列表
     */
    public List<Message> buildMessages(String tenantName, String message,
                                       List<AgentSession.AgentMessageItem> history, String memoryText) {
        String template = promptRepository.get("agent.system");
        String memory = memoryText == null || memoryText.isBlank() ? "无" : memoryText;
        String system = template
                .replace("{小区名}", tenantName == null ? "本小区" : tenantName)
                .replace("{历史记忆}", memory);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(system));
        // 多轮历史：仅 user/assistant（tool 为工具中间产物，跳过避免上下文噪声）
        if (history != null) {
            for (AgentSession.AgentMessageItem h : history) {
                if (AgentMessageRole.USER.equals(h.role())) {
                    messages.add(new UserMessage(h.content() == null ? "" : h.content()));
                } else if (AgentMessageRole.ASSISTANT.equals(h.role())
                        && h.content() != null && !h.content().isBlank()) {
                    messages.add(new AssistantMessage(h.content()));
                }
            }
        }
        messages.add(new UserMessage(message));
        return messages;
    }
}
