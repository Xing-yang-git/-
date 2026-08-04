package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.search.KnowledgeHit;
import com.platform.ai.search.KnowledgeRetrievalService;
import com.platform.common.AgentMessageRole;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.User;
import com.platform.repository.TenantRepository;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentService 小邻对话编排单元测试 — 覆盖 RAG 检索 → 回复生成 → 意图解析全链路。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentService 对话编排单元测试")
class AgentServiceTest {

    @Mock
    private KnowledgeRetrievalService retrievalService;
    @Mock
    private AgentPromptBuilder promptBuilder;
    @Mock
    private AgentToolDispatcher toolDispatcher;
    @Mock
    private IntentRouter intentRouter;
    @Mock
    private SessionService sessionService;
    @Mock
    private ArchiveService archiveService;
    @Mock
    private OpenAiChatModel deepseekChatModel;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRepository tenantRepository;

    private AgentService agentService;

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 10L;

    @BeforeEach
    void setUp() {
        agentService = new AgentService(retrievalService, promptBuilder, toolDispatcher, intentRouter,
                sessionService, archiveService, deepseekChatModel, userRepository, tenantRepository,
                new ObjectMapper());
        ReflectionTestUtils.setField(agentService, "archiveMessageCount", 20);
    }

    private User userWithTenant() {
        return User.builder().id(USER_ID).tenantId(TENANT_ID).build();
    }

    private List<KnowledgeHit> hits() {
        return List.of(new KnowledgeHit(1L, "装修时间规定", "工作日 8:00-12:00", "rules", "小区规章制度", 0.1));
    }

    private void stubCommon(String message) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithTenant()));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(Tenant.builder().id(TENANT_ID).name("阳光花园").build()));
        when(retrievalService.search(TENANT_ID, message)).thenReturn(hits());
        when(sessionService.getHistory(USER_ID)).thenReturn(List.of());
        when(promptBuilder.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.<Message>of(new UserMessage(message)));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("对话 - 普通回答时返回回复与引用来源，无动作卡片")
    void should_returnReply_when_ordinaryAnswer() {
        stubCommon("你好");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("你好，我是小邻，有什么可以帮您？"));
        when(intentRouter.parse(any())).thenReturn(null);

        AgentChatResult result = agentService.chat(USER_ID, "你好");

        assertThat(result.reply()).isEqualTo("你好，我是小邻，有什么可以帮您？");
        assertThat(result.sources()).hasSize(1);
        assertThat(result.actions()).isEmpty();
        // 用户消息 + AI 回复各写入一次热会话
        verify(sessionService).append(eq(USER_ID), eq(AgentMessageRole.USER), eq("你好"), eq(null), eq(null));
        verify(sessionService).append(eq(USER_ID), eq(AgentMessageRole.ASSISTANT), eq(result.reply()), any(), any());
    }

    @Test
    @DisplayName("对话 - 命中写操作意图时返回动作卡片并剔除 JSON")
    void should_returnActionCard_when_intentDetected() {
        String reply = "好的，已为您准备 {\"intent\":\"publish_help\",\"params\":{\"title\":\"搬家\"}}";
        stubCommon("帮我发起搬家求助");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response(reply));
        AgentAction action = new AgentAction("publish_help", "帮您发起求助", Map.of("title", "搬家"));
        when(intentRouter.parse(reply)).thenReturn(action);
        when(intentRouter.stripJson(reply)).thenReturn("好的，已为您准备");

        AgentChatResult result = agentService.chat(USER_ID, "帮我发起搬家求助");

        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().get(0).type()).isEqualTo("publish_help");
        assertThat(result.reply()).isEqualTo("好的，已为您准备");
    }

    @Test
    @DisplayName("对话 - 剔除 JSON 后为空时替换为友好提示")
    void should_replaceBlankReply_when_jsonOnly() {
        String reply = "{\"intent\":\"publish_idle\",\"params\":{\"title\":\"电钻\"}}";
        stubCommon("帮我发布闲置");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response(reply));
        AgentAction action = new AgentAction("publish_idle", "帮您发布闲置", Map.of());
        when(intentRouter.parse(reply)).thenReturn(action);
        when(intentRouter.stripJson(reply)).thenReturn("");

        AgentChatResult result = agentService.chat(USER_ID, "帮我发布闲置");

        assertThat(result.reply()).isEqualTo("已为您准备好发布草稿，请点击下方卡片确认填写。");
        assertThat(result.actions()).hasSize(1);
    }

    @Test
    @DisplayName("对话 - 用户不存在时抛业务异常")
    void should_throw_when_userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentService.chat(USER_ID, "你好"))
                .isInstanceOf(BizException.class)
                .hasMessage("用户不存在");
    }

    @Test
    @DisplayName("对话 - 模型返回空回复时抛 AI 生成异常")
    void should_throw_when_replyBlank() {
        stubCommon("你好");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("   "));

        assertThatThrownBy(() -> agentService.chat(USER_ID, "你好"))
                .isInstanceOf(AiGenerationException.class)
                .hasMessageContaining("AI 回复为空");
    }

    @Test
    @DisplayName("对话 - 达到归档阈值时触发归档")
    void should_archive_when_thresholdReached() {
        ReflectionTestUtils.setField(agentService, "archiveMessageCount", 2);
        stubCommon("你好");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("回复内容"));
        when(intentRouter.parse(any())).thenReturn(null);
        AgentSession session = new AgentSession();
        session.setMessages(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "m1", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "m2", null, null)));
        when(sessionService.getSession(USER_ID)).thenReturn(session);

        agentService.chat(USER_ID, "你好");

        verify(archiveService).archive(USER_ID);
    }

    @Test
    @DisplayName("对话 - 未达归档阈值时不触发归档")
    void should_notArchive_when_belowThreshold() {
        stubCommon("你好");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("回复内容"));
        when(intentRouter.parse(any())).thenReturn(null);
        AgentSession session = new AgentSession();
        session.setMessages(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "m1", null, null)));
        when(sessionService.getSession(USER_ID)).thenReturn(session);

        agentService.chat(USER_ID, "你好");

        verify(archiveService, org.mockito.Mockito.never()).archive(USER_ID);
    }

    @Test
    @DisplayName("对话 - 小区不存在时兜底使用「本小区」")
    void should_useDefaultTenantName_when_tenantNotFound() {
        stubCommon("你好");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("回复内容"));
        when(intentRouter.parse(any())).thenReturn(null);

        agentService.chat(USER_ID, "你好");

        verify(promptBuilder).buildMessages(eq("本小区"), eq("你好"), any(), any());
    }

    @Test
    @DisplayName("对话 - 用户未绑定小区时兜底使用「本小区」")
    void should_useDefaultTenantName_when_userHasNoTenant() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(User.builder().id(USER_ID).tenantId(null).build()));
        when(retrievalService.search(null, "你好")).thenReturn(hits());
        when(sessionService.getHistory(USER_ID)).thenReturn(List.of());
        when(promptBuilder.buildMessages(any(), any(), any(), any()))
                .thenReturn(List.<Message>of(new UserMessage("你好")));
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("回复内容"));
        when(intentRouter.parse(any())).thenReturn(null);

        agentService.chat(USER_ID, "你好");

        verify(promptBuilder).buildMessages(eq("本小区"), eq("你好"), any(), any());
        // 未绑定小区不解析租户名
        verify(tenantRepository, org.mockito.Mockito.never()).findById(any());
    }
}
