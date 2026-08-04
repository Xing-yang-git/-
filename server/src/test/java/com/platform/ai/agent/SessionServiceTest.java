package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.platform.common.AgentMessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SessionService 热会话服务单元测试 — 覆盖 Redis 读写、降级与截断逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService 热会话服务单元测试")
class SessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** 会话含 LocalDateTime（lastActive），必须注册 JavaTimeModule 才能序列化 */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sessionService = new SessionService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(sessionService, "sessionTtlHours", 24);
        ReflectionTestUtils.setField(sessionService, "maxTurns", 10);
        ReflectionTestUtils.setField(sessionService, "maxHistoryChars", 6000);
    }

    @Test
    @DisplayName("读取会话 - Redis 无数据时返回 null")
    void should_returnNull_when_sessionNotInRedis() {
        when(valueOperations.get("agent:session:1")).thenReturn(null);

        AgentSession session = sessionService.getSession(1L);

        assertThat(session).isNull();
    }

    @Test
    @DisplayName("读取会话 - Redis 有 JSON 时反序列化返回")
    void should_returnSession_when_jsonStoredInRedis() throws Exception {
        AgentSession stored = new AgentSession();
        stored.setConversationId(9L);
        stored.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "你好", null, null))));
        when(valueOperations.get("agent:session:1")).thenReturn(objectMapper.writeValueAsString(stored));

        AgentSession session = sessionService.getSession(1L);

        assertThat(session).isNotNull();
        assertThat(session.getConversationId()).isEqualTo(9L);
        assertThat(session.getMessages()).hasSize(1);
        assertThat(session.getMessages().get(0).content()).isEqualTo("你好");
    }

    @Test
    @DisplayName("读取会话 - Redis 异常时降级返回 null")
    void should_returnNull_when_redisUnavailable() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        AgentSession session = sessionService.getSession(1L);

        assertThat(session).isNull();
    }

    @Test
    @DisplayName("获取历史 - 无会话时返回空列表")
    void should_returnEmptyList_when_noSession() {
        when(valueOperations.get("agent:session:1")).thenReturn(null);

        List<AgentSession.AgentMessageItem> history = sessionService.getHistory(1L);

        assertThat(history).isEmpty();
    }

    @Test
    @DisplayName("追加消息 - 已有会话时追加并写回 Redis")
    void should_appendAndSave_when_sessionExists() throws Exception {
        AgentSession existing = new AgentSession();
        existing.setConversationId(5L);
        existing.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "你好", null, null))));
        when(valueOperations.get("agent:session:1")).thenReturn(objectMapper.writeValueAsString(existing));

        AgentSession result = sessionService.append(1L, AgentMessageRole.USER, "有什么能帮忙的？", null, null);

        assertThat(result.getMessages()).hasSize(2);
        assertThat(result.getMessages().get(1).content()).isEqualTo("有什么能帮忙的？");
        verify(valueOperations).set(eq("agent:session:1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("追加消息 - 无会话时创建新会话")
    void should_appendNewSession_when_noSession() {
        when(valueOperations.get("agent:session:1")).thenReturn(null);

        AgentSession result = sessionService.append(1L, AgentMessageRole.ASSISTANT, "您好！", null, null);

        assertThat(result.getMessages()).hasSize(1);
        assertThat(result.getMessages().get(0).role()).isEqualTo(AgentMessageRole.ASSISTANT);
        verify(valueOperations).set(eq("agent:session:1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("消息截断 - 超 max-turns×2 条时丢弃最旧消息")
    void should_truncateMessages_when_overMaxTurns() throws Exception {
        ReflectionTestUtils.setField(sessionService, "maxTurns", 2);   // 上限 4 条
        AtomicReference<AgentSession> store = new AtomicReference<>();
        when(valueOperations.get(anyString())).thenAnswer(inv -> {
            AgentSession s = store.get();
            return s == null ? null : objectMapper.writeValueAsString(s);
        });
        doAnswer(inv -> {
            store.set(objectMapper.readValue((String) inv.getArgument(1), AgentSession.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        for (int i = 1; i <= 5; i++) {
            sessionService.append(1L, AgentMessageRole.USER, "msg" + i, null, null);
        }

        AgentSession result = sessionService.getSession(1L);
        assertThat(result.getMessages()).hasSize(4);
        assertThat(result.getMessages().get(0).content()).isEqualTo("msg2");
        assertThat(result.getMessages().get(3).content()).isEqualTo("msg5");
    }

    @Test
    @DisplayName("字符裁剪 - 超 max-history-chars 时从头部裁剪且至少保留 6 条")
    void should_trimChars_keepAtLeastSix() throws Exception {
        ReflectionTestUtils.setField(sessionService, "maxHistoryChars", 5);   // 极小值触发裁剪
        AtomicReference<AgentSession> store = new AtomicReference<>();
        when(valueOperations.get(anyString())).thenAnswer(inv -> {
            AgentSession s = store.get();
            return s == null ? null : objectMapper.writeValueAsString(s);
        });
        doAnswer(inv -> {
            store.set(objectMapper.readValue((String) inv.getArgument(1), AgentSession.class));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        for (int i = 1; i <= 8; i++) {
            sessionService.append(1L, AgentMessageRole.USER, "ab", null, null);
        }

        AgentSession result = sessionService.getSession(1L);
        assertThat(result.getMessages()).hasSize(6);
        // 丢弃了前 2 条（msg1、msg2 为占位），最后一条仍在
        assertThat(result.getMessages().get(result.getMessages().size() - 1).content()).isEqualTo("ab");
    }

    @Test
    @DisplayName("写入会话 - Redis 异常时静默降级不抛出")
    void should_silentlyIgnore_when_redisWriteFails() {
        when(valueOperations.get("agent:session:1")).thenReturn(null);
        doAnswer(inv -> {
            throw new RuntimeException("redis down");
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // 不抛出即视为降级成功
        sessionService.append(1L, AgentMessageRole.USER, "你好", null, null);

        verify(valueOperations).set(eq("agent:session:1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("读取会话 - 空 JSON 记录返回 null")
    void should_returnNull_when_emptyJsonStored() {
        when(valueOperations.get("agent:session:1")).thenReturn("");

        AgentSession session = sessionService.getSession(1L);

        // 反序列化失败降级为无会话
        assertThat(session).isNull();
    }
}
