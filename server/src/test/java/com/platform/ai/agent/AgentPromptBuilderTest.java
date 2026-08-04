package com.platform.ai.agent;

import com.platform.ai.search.KnowledgeHit;
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
 * AgentPromptBuilder Prompt 组装单元测试 — 覆盖知识库命中/未命中与历史消息过滤。
 */
@DisplayName("AgentPromptBuilder Prompt 组装单元测试")
class AgentPromptBuilderTest {

    private final AgentPromptBuilder builder = new AgentPromptBuilder();

    private List<KnowledgeHit> hits() {
        return List.of(
                new KnowledgeHit(1L, "装修时间规定", "工作日 8:00-12:00", "rules", "小区规章制度", 0.1),
                new KnowledgeHit(2L, "垃圾投放", "分类投放", "service", "小区服务手册", 0.2));
    }

    @Test
    @DisplayName("组装 - 知识库命中时注入上下文块与来源")
    void should_buildSystemPrompt_when_hitsPresent() {
        List<Message> messages = builder.buildMessages("阳光花园", "装修几点？", hits(), List.of());

        assertThat(messages).hasSize(2);   // system + user
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        String system = messages.get(0).getText();
        assertThat(system).contains("你是阳光花园小区的智能助手「小邻」");
        assertThat(system).contains("[1] 来源：《小区规章制度》 | 分类：rules");
        assertThat(system).contains("工作日 8:00-12:00");
        assertThat(system).contains("根据《小区规章制度》");
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("装修几点？");
    }

    @Test
    @DisplayName("组装 - 无知识库命中时使用无 KB 提示词")
    void should_buildNoKbPrompt_when_noHits() {
        List<Message> messages = builder.buildMessages("阳光花园", "你好", List.of(), List.of());

        String system = messages.get(0).getText();
        assertThat(system).contains("知识库暂无相关信息时明确说");
        assertThat(system).doesNotContain("以下是知识库检索到的资料");
    }

    @Test
    @DisplayName("组装 - 命中列表为空（null）时按无 KB 处理")
    void should_buildNoKbPrompt_when_hitsNull() {
        List<Message> messages = builder.buildMessages("阳光花园", "你好", null, List.of());

        assertThat(messages.get(0).getText()).doesNotContain("以下是知识库检索到的资料");
    }

    @Test
    @DisplayName("组装 - 历史仅保留 user/assistant，跳过 tool 与空白 assistant")
    void should_filterHistory_skipToolAndBlank() {
        List<AgentSession.AgentMessageItem> history = List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "我想借电钻", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.TOOL, "工具中间产物", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "好的，为您查询", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "   ", null, null));

        List<Message> messages = builder.buildMessages("阳光花园", "还有吗？", List.of(), history);

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
        List<Message> messages = builder.buildMessages("阳光花园", "你好", List.of(), null);

        assertThat(messages).hasSize(2);
    }

    @Test
    @DisplayName("组装 - 多命中按编号排序生成上下文块")
    void should_numberHits_inOrder() {
        List<Message> messages = builder.buildMessages("阳光花园", "怎么扔垃圾", hits(), List.of());

        String system = messages.get(0).getText();
        assertThat(system).contains("[2] 来源：《小区服务手册》 | 分类：service");
    }
}
