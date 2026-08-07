package com.platform.ai.agent;

import com.platform.ai.common.PromptRepository;
import com.platform.common.AgentMessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentPromptBuilder Prompt 组装单元测试 — 覆盖 System Prompt 占位符渲染、知识库工具化描述与历史消息过滤。
 *
 * <p>知识检索工具化（3b）后：System Prompt 从 {@code prompts/agent/system.md} 读取，运行时只替换
 * {@code {小区名}} 与 {@code {历史记忆}} 占位符，不再拼接知识库命中块（检索改由模型按需调工具）。</p>
 */
@DisplayName("AgentPromptBuilder Prompt 组装单元测试")
class AgentPromptBuilderTest {

    // 与 PromptRepositoryTest 同一假设：classpath 下存在 prompts 提示词文件，构造期加载
    private final AgentPromptBuilder builder = new AgentPromptBuilder(new PromptRepository());

    @Test
    @DisplayName("组装 - System Prompt 渲染小区名并包含知识库能力与安全规则描述")
    void should_buildSystemPrompt_when_validTenantName() {
        List<Message> messages = builder.buildMessages("阳光花园", "装修几点？", List.of());

        assertThat(messages).hasSize(2);   // system + user
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        String system = messages.get(0).getText();
        // {小区名} 占位符被渲染为租户名（模板为「{小区名}小区」，渲染后为「阳光花园小区」）
        assertThat(system).contains("你是「小邻」，阳光花园小区的智能助手");
        // 知识库工具化能力描述（决策路由场景 B）
        assertThat(system).contains("检索小区知识库");
        // 安全规则（注入防护，MessagePreFilter 注入特征提示也引用此节）
        assertThat(system).contains("【8. 安全规则】");
        // {历史记忆} 占位符固定渲染为「无」（本期无记忆注入）
        assertThat(system).doesNotContain("{历史记忆}");
        // 不再有「知识库命中/未命中」双分支痕迹：hits 不再拼入 System Prompt
        assertThat(system).doesNotContain("以下是知识库检索到的资料");
        assertThat(system).doesNotContain("知识库暂无相关信息时明确说");
        assertThat(system).doesNotContain("[1] 来源：《");
        assertThat(system).doesNotContain("根据《小区规章制度》");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("装修几点？");
    }

    @Test
    @DisplayName("组装 - 小区名为 null 时兜底渲染「本小区」")
    void should_renderDefaultTenantName_when_tenantNull() {
        List<Message> messages = builder.buildMessages(null, "你好", List.of());

        assertThat(messages.get(0).getText()).contains("你是「小邻」，本小区小区的智能助手");
    }

    @Test
    @DisplayName("组装 - 历史仅保留 user/assistant，跳过 tool 与空白 assistant")
    void should_filterHistory_skipToolAndBlank() {
        List<AgentSession.AgentMessageItem> history = List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "我想借电钻", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.TOOL, "工具中间产物", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "好的，为您查询", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "   ", null, null));

        List<Message> messages = builder.buildMessages("阳光花园", "还有吗？", history);

        // system + 2 条历史（user + assistant）+ 最新 user
        assertThat(messages).hasSize(4);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("我想借电钻");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo("好的，为您查询");
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("还有吗？");
    }

    @Test
    @DisplayName("组装 - 历史为 null 时仅 system + user")
    void should_buildWithoutHistory_when_historyNull() {
        List<Message> messages = builder.buildMessages("阳光花园", "你好", null);

        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("组装 - 历史为空列表时仅 system + user")
    void should_buildWithoutHistory_when_historyEmpty() {
        List<Message> messages = builder.buildMessages("阳光花园", "你好", List.of());

        assertThat(messages).hasSize(2);
    }
}
