package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IntentRouter 意图路由单元测试 — 覆盖 JSON 意图解析、动作卡片生成与 JSON 剔除。
 */
@DisplayName("IntentRouter 意图路由单元测试")
class IntentRouterTest {

    private final IntentRouter intentRouter = new IntentRouter(new ObjectMapper());

    @Test
    @DisplayName("解析 - 命中写操作意图时生成动作卡片")
    void should_parse_when_validIntent() {
        String reply = "好的，马上帮您。{\"intent\":\"publish_help\",\"params\":{\"title\":\"搬家\"}}";

        AgentAction action = intentRouter.parse(reply);

        assertThat(action).isNotNull();
        assertThat(action.type()).isEqualTo("publish_help");
        assertThat(action.label()).isEqualTo("帮您发起求助");
        assertThat(action.params()).containsEntry("title", "搬家");
    }

    @Test
    @DisplayName("解析 - markdown 代码块包裹的 JSON 也能解析")
    void should_parse_when_markdownWrapped() {
        String reply = "```json\n{\"intent\":\"publish_idle\",\"params\":{\"title\":\"电钻\"}}\n```";

        AgentAction action = intentRouter.parse(reply);

        assertThat(action).isNotNull();
        assertThat(action.type()).isEqualTo("publish_idle");
        assertThat(action.label()).isEqualTo("帮您发布闲置");
    }

    @Test
    @DisplayName("解析 - 未知 intent 返回 null")
    void should_returnNull_when_unknownIntent() {
        AgentAction action = intentRouter.parse("{\"intent\":\"delete_all\",\"params\":{}}");

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("解析 - 非 JSON 普通回答返回 null")
    void should_returnNull_when_notJson() {
        AgentAction action = intentRouter.parse("今天天气不错，有什么可以帮您？");

        assertThat(action).isNull();
    }

    @Test
    @DisplayName("解析 - 空回复返回 null")
    void should_returnNull_when_blankReply() {
        assertThat(intentRouter.parse(null)).isNull();
        assertThat(intentRouter.parse("   ")).isNull();
    }

    @Test
    @DisplayName("剔除 - 从回复中剔除 JSON 意图段保留自然语言")
    void should_stripJson_keepNaturalLanguage() {
        String reply = "好的，已为您准备。{\"intent\":\"publish_help\",\"params\":{}}";

        String stripped = intentRouter.stripJson(reply);

        assertThat(stripped).isEqualTo("好的，已为您准备。");
    }

    @Test
    @DisplayName("剔除 - 纯 JSON 回复剔除后为空")
    void should_stripJson_empty_when_jsonOnly() {
        String reply = "{\"intent\":\"publish_idle\",\"params\":{}}";

        assertThat(intentRouter.stripJson(reply)).isEmpty();
    }

    @Test
    @DisplayName("剔除 - 无 JSON 时原样返回并去首尾空白")
    void should_stripJson_returnOriginal_when_noJson() {
        assertThat(intentRouter.stripJson("  普通回答  ")).isEqualTo("普通回答");
        assertThat(intentRouter.stripJson(null)).isEmpty();
    }

    @Test
    @DisplayName("解析 - params 为空对象时返回空 Map")
    void should_parse_emptyParams_when_missing() {
        AgentAction action = intentRouter.parse("{\"intent\":\"publish_wanted\",\"params\":{}}");

        assertThat(action).isNotNull();
        assertThat(action.type()).isEqualTo("publish_wanted");
        assertThat(action.params()).isEmpty();
    }
}
