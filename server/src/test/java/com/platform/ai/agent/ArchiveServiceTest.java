package com.platform.ai.agent;

import com.platform.common.AgentConversationStatus;
import com.platform.common.AgentMessageRole;
import com.platform.common.BizException;
import com.platform.model.entity.AgentConversation;
import com.platform.model.entity.AgentMessage;
import com.platform.model.entity.User;
import com.platform.repository.AgentConversationRepository;
import com.platform.repository.AgentMessageRepository;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ArchiveService 会话归档服务单元测试 — 覆盖归档/列表/恢复/软删全流程。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ArchiveService 会话归档服务单元测试")
class ArchiveServiceTest {

    @Mock
    private SessionService sessionService;
    @Mock
    private AgentConversationRepository conversationRepository;
    @Mock
    private AgentMessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(archiveService, "resumeTurns", 10);
    }

    private AgentSession sessionWithMessages(int count) {
        AgentSession session = new AgentSession();
        List<AgentSession.AgentMessageItem> messages = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            messages.add(new AgentSession.AgentMessageItem(AgentMessageRole.USER, "消息" + i, null, null));
        }
        session.setMessages(messages);
        return session;
    }

    @Test
    @DisplayName("归档 - 无热会话时跳过")
    void should_skip_when_noSession() {
        when(sessionService.getSession(1L)).thenReturn(null);

        archiveService.archive(1L);

        verify(messageRepository, never()).saveAll(anyList());
        verify(conversationRepository, never()).save(any(AgentConversation.class));
    }

    @Test
    @DisplayName("归档 - 热会话无消息时跳过")
    void should_skip_when_sessionEmpty() {
        AgentSession session = new AgentSession();
        session.setMessages(new ArrayList<>());
        when(sessionService.getSession(1L)).thenReturn(session);

        archiveService.archive(1L);

        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("归档 - 用户未绑定小区时跳过（防 NOT NULL 冲突）")
    void should_skip_when_userNotBoundToTenant() {
        when(sessionService.getSession(1L)).thenReturn(sessionWithMessages(2));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(null).build()));

        archiveService.archive(1L);

        verify(conversationRepository, never()).save(any(AgentConversation.class));
        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("归档 - 新建会话并追加消息、清空热会话")
    void should_createConversation_andSaveMessages() {
        AgentSession session = sessionWithMessages(2);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        // 热会话无 conversationId → 走新建分支
        // 模拟 JPA 生成主键
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(inv -> {
            AgentConversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(1L);
            }
            return c;
        });

        archiveService.archive(1L);

        // 会话被归档（ARCHIVED）且标题取首条消息
        ArgumentCaptor<AgentConversation> convCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, times(2)).save(convCaptor.capture());
        AgentConversation created = convCaptor.getAllValues().get(0);
        assertThat(created.getStatus()).isEqualTo(AgentConversationStatus.ARCHIVED);
        assertThat(created.getTenantId()).isEqualTo(10L);
        assertThat(created.getTitle()).isEqualTo("消息1");
        assertThat(created.getMessageCount()).isEqualTo(2);

        verify(messageRepository).saveAll(anyList());

        // 归档后热会话清空并回填 conversationId
        ArgumentCaptor<AgentSession> sessionCaptor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), sessionCaptor.capture());
        AgentSession saved = sessionCaptor.getValue();
        assertThat(saved.getConversationId()).isEqualTo(1L);
        assertThat(saved.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("归档 - 复用既有 ARCHIVED 会话追加消息，不重复建行")
    void should_reuseArchivedConversation() {
        AgentSession session = sessionWithMessages(2);
        session.setConversationId(9L);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        AgentConversation existing = AgentConversation.builder()
                .id(9L).userId(1L).tenantId(10L).messageCount(5)
                .status(AgentConversationStatus.ARCHIVED).build();
        when(conversationRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(conversationRepository.save(existing)).thenReturn(existing);

        archiveService.archive(1L);

        // 仅追加消息 + 更新计数，不新建会话
        verify(conversationRepository, times(1)).save(existing);
        verify(messageRepository).saveAll(anyList());
        assertThat(existing.getMessageCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("归档 - 标题超过 20 字时截断")
    void should_truncateTitle_when_overTwentyChars() {
        String longTitle = "这是一个非常非常非常非常非常长的用户首条消息超过二十个字";
        AgentSession session = new AgentSession();
        session.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "AI 回复", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, longTitle, null, null))));
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(inv -> inv.getArgument(0));

        archiveService.archive(1L);

        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        // 标题取首条 user 消息并截断到 20 字（跳过 assistant 消息）
        assertThat(captor.getAllValues().get(0).getTitle()).isEqualTo(longTitle.substring(0, 20));
    }

    @Test
    @DisplayName("列表 - 缺失字段回填默认值")
    void should_list_withDefaults_when_fieldsNull() {
        AgentConversation c = AgentConversation.builder()
                .id(1L).title(null).messageCount(null).status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0)).updatedAt(null).build();
        Page<AgentConversation> page = new PageImpl<>(List.of(c));
        when(conversationRepository.findByUserIdAndStatusNot(1L, AgentConversationStatus.DELETED, PageRequest.of(0, 10)))
                .thenReturn(page);

        Page<Map<String, Object>> result = archiveService.list(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        Map<String, Object> row = result.getContent().get(0);
        assertThat(row.get("title")).isEqualTo("未命名对话");
        assertThat(row.get("messageCount")).isEqualTo(0);
        assertThat(row.get("updatedAt")).isEqualTo(c.getCreatedAt());
        assertThat(row.get("status")).isEqualTo(AgentConversationStatus.ARCHIVED);
    }

    @Test
    @DisplayName("恢复 - 归档会话不存在时抛业务异常")
    void should_throw_when_conversationNotFound_onResume() {
        when(sessionService.getSession(1L)).thenReturn(null);
        when(conversationRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> archiveService.resume(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话不存在或无权访问");
    }

    @Test
    @DisplayName("恢复 - 已软删会话抛业务异常")
    void should_throw_when_conversationDeleted_onResume() {
        when(sessionService.getSession(1L)).thenReturn(null);
        AgentConversation deleted = AgentConversation.builder()
                .id(9L).userId(1L).status(AgentConversationStatus.DELETED).build();
        when(conversationRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> archiveService.resume(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话已删除");
    }

    @Test
    @DisplayName("恢复 - 回填最近 resumeTurns×2 条并清空 conversationId")
    void should_resume_withRecentTurnsBackfill() {
        when(sessionService.getSession(1L)).thenReturn(null);
        AgentConversation conversation = AgentConversation.builder()
                .id(9L).userId(1L).status(AgentConversationStatus.ARCHIVED).build();
        when(conversationRepository.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(conversation));
        ReflectionTestUtils.setField(archiveService, "resumeTurns", 2);

        List<AgentMessage> archived = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            archived.add(AgentMessage.builder().conversationId(9L).role(AgentMessageRole.USER)
                    .content("归档消息" + i).build());
        }
        when(messageRepository.findByConversationIdOrderByIdAsc(9L)).thenReturn(archived);

        archiveService.resume(1L, 9L);

        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), captor.capture());
        AgentSession saved = captor.getValue();
        assertThat(saved.getMessages()).hasSize(4);
        assertThat(saved.getMessages().get(0).content()).isEqualTo("归档消息3");
        assertThat(saved.getMessages().get(3).content()).isEqualTo("归档消息6");
        assertThat(saved.getConversationId()).isNull();
    }

    @Test
    @DisplayName("软删 - 仅删除归属用户的会话并返回数量")
    void should_softDelete_onlyOwnedConversations() {
        AgentConversation owned = AgentConversation.builder()
                .id(1L).userId(1L).status(AgentConversationStatus.ARCHIVED).build();
        when(conversationRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(owned));
        when(conversationRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.empty());

        int count = archiveService.softDelete(1L, List.of(1L, 2L));

        assertThat(count).isEqualTo(1);
        assertThat(owned.getStatus()).isEqualTo(AgentConversationStatus.DELETED);
        verify(conversationRepository).save(owned);
    }

    @Test
    @DisplayName("软删 - 空列表直接返回 0")
    void should_softDelete_zero_when_emptyList() {
        int count = archiveService.softDelete(1L, List.of());

        assertThat(count).isZero();
        verify(conversationRepository, never()).save(any(AgentConversation.class));
    }
}
