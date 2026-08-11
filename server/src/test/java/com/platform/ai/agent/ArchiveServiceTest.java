package com.platform.ai.agent;

import com.platform.common.AgentConversationStatus;
import com.platform.common.AgentMessageRole;
import com.platform.common.BizException;
import com.platform.model.entity.AgentConversation;
import com.platform.model.entity.AgentMemorySegment;
import com.platform.model.entity.AgentMessage;
import com.platform.model.entity.User;
import com.platform.repository.AgentConversationRepository;
import com.platform.repository.AgentMemorySegmentRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ArchiveService 会话归档服务单元测试 — 覆盖滑动窗口归档 / 剩余归档 / 列表分组 / 恢复 / 软删全流程。
 *
 * <p>滑动窗口设计：每次归档创建一条新行（title 由压缩流程异步回填为 null），同一会话通过会话级
 * conversation_id 关联（首次归档行 conversation_id = 自身 id，后续沿用）；归档后触发异步压缩。</p>
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
    @Mock
    private AgentMemorySegmentRepository memorySegmentRepository;
    @Mock
    private MemoryCompressionService memoryCompressionService;

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

    /** 模拟 JPA 生成主键：save 时若 id 为空则赋予指定 id */
    private void stubSaveAssigningIds(Long id) {
        when(conversationRepository.save(any(AgentConversation.class))).thenAnswer(inv -> {
            AgentConversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(id);
            }
            return c;
        });
    }

    // ==================== 剩余归档（archiveRemaining / 旧 archive 兼容入口） ====================

    @Test
    @DisplayName("剩余归档 - 无热会话时返回 null 不建行")
    void should_skip_when_noSession() {
        when(sessionService.getSession(1L)).thenReturn(null);

        assertThat(archiveService.archiveRemaining(1L)).isNull();

        verify(messageRepository, never()).saveAll(anyList());
        verify(conversationRepository, never()).save(any(AgentConversation.class));
    }

    @Test
    @DisplayName("剩余归档 - 热会话无消息时返回 null 不建行")
    void should_skip_when_sessionEmpty() {
        AgentSession session = new AgentSession();
        session.setMessages(new ArrayList<>());
        when(sessionService.getSession(1L)).thenReturn(session);

        assertThat(archiveService.archiveRemaining(1L)).isNull();

        verify(messageRepository, never()).saveAll(anyList());
        verify(conversationRepository, never()).save(any(AgentConversation.class));
    }

    @Test
    @DisplayName("剩余归档 - 用户未绑定小区时返回 null 不建行（防 NOT NULL 冲突）")
    void should_skip_when_userNotBoundToTenant() {
        when(sessionService.getSession(1L)).thenReturn(sessionWithMessages(2));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(null).build()));

        assertThat(archiveService.archiveRemaining(1L)).isNull();

        verify(conversationRepository, never()).save(any(AgentConversation.class));
        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("剩余归档 - 新建归档行并写消息、清空热会话、回填会话级 id")
    void should_createConversation_andSaveMessages() {
        AgentSession session = sessionWithMessages(2);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(1L);

        archiveService.archiveRemaining(1L);

        // 归档行：status=ARCHIVED、messageCount=2、标题留空（压缩流程异步回填）、会话级 id=自身 id（首次归档）
        ArgumentCaptor<AgentConversation> convCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, times(2)).save(convCaptor.capture());
        AgentConversation created = convCaptor.getAllValues().get(0);
        assertThat(created.getStatus()).isEqualTo(AgentConversationStatus.ARCHIVED);
        assertThat(created.getTenantId()).isEqualTo(10L);
        assertThat(created.getTitle()).isNull();
        assertThat(created.getMessageCount()).isEqualTo(2);

        verify(messageRepository).saveAll(anyList());

        // 归档后热会话清空；最后一次保存清空会话级 conversationId（会话结束语义，新会话不合并历史）
        ArgumentCaptor<AgentSession> sessionCaptor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService, times(2)).saveSession(eq(1L), sessionCaptor.capture());
        AgentSession saved = sessionCaptor.getValue();
        assertThat(saved.getConversationId()).isNull();
        assertThat(saved.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("剩余归档 - 归档行标题留空（标题截断逻辑已移至压缩服务，归档同步路径零 LLM 调用）")
    void should_leaveTitleNull_when_archiving() {
        String longTitle = "这是一个非常非常非常非常非常长的用户首条消息超过二十个字";
        AgentSession session = new AgentSession();
        session.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "AI 回复", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, longTitle, null, null))));
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(1L);

        archiveService.archiveRemaining(1L);

        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getTitle()).isNull();
    }

    // ==================== 滑动窗口归档（archiveWindow） ====================

    @Test
    @DisplayName("滑动窗口 - 最旧 N 条归档为新行，Redis 保留剩余，触发异步压缩")
    void should_archiveWindow_when_overWindowSize() {
        AgentSession session = sessionWithMessages(5);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(50L);

        Long rowId = archiveService.archiveWindow(1L, 2);

        // 新行：归档最旧 2 条，messageCount=2，标题留空，首次归档 conversation_id=自身 id
        ArgumentCaptor<AgentConversation> convCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(convCaptor.capture());
        AgentConversation created = convCaptor.getAllValues().get(0);
        assertThat(rowId).isEqualTo(50L);
        assertThat(created.getTitle()).isNull();
        assertThat(created.getMessageCount()).isEqualTo(2);
        assertThat(created.getConversationId()).isEqualTo(50L);

        // Redis 移走最旧 2 条，保留剩余 3 条（记忆按次实时检索，无窗口缓存字段）
        ArgumentCaptor<AgentSession> sessionCaptor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), sessionCaptor.capture());
        AgentSession saved = sessionCaptor.getValue();
        assertThat(saved.getMessages()).hasSize(3);
        assertThat(saved.getMessages().get(0).content()).isEqualTo("消息3");
        assertThat(saved.getConversationId()).isEqualTo(50L);

        // 异步触发压缩（segmentNo = 已用序号 + 1 = 1）
        verify(memoryCompressionService).compressWindow(eq(1L), eq(10L), eq(50L), eq(50L), anyString(), eq(1));
    }

    @Test
    @DisplayName("滑动窗口 - windowSize 超过消息数时全部归档")
    void should_archiveAll_when_windowLargerThanMessages() {
        AgentSession session = sessionWithMessages(3);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(50L);

        archiveService.archiveWindow(1L, 10);

        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getMessageCount()).isEqualTo(3);
        // Redis 清空
        ArgumentCaptor<AgentSession> sc = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), sc.capture());
        assertThat(sc.getValue().getMessages()).isEmpty();
    }

    @Test
    @DisplayName("滑动窗口 - 已有会话级 id 时后续归档行沿用 conversation_id（每窗口新行，不复用旧行）")
    void should_reuseConversationId_when_alreadyArchived() {
        AgentSession session = sessionWithMessages(3);
        session.setConversationId(9L);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(50L);

        archiveService.archiveWindow(1L, 2);

        // 新行（id=50），但 conversation_id 沿用既有会话级 id 9
        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        AgentConversation created = captor.getAllValues().get(0);
        assertThat(created.getId()).isEqualTo(50L);
        assertThat(created.getConversationId()).isEqualTo(9L);

        ArgumentCaptor<AgentSession> sc = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), sc.capture());
        assertThat(sc.getValue().getConversationId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("滑动窗口 - 只归档已归档回填前缀之外的新增消息（resume 后续对话不重复落库）")
    void should_archiveWindow_onlyUnarchived_when_hasBackfilledPrefix() {
        AgentSession session = new AgentSession();
        session.setConversationId(9L);
        session.setArchivedPrefixCount(2);
        session.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "回填1", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "回填2", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "新增1", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "新增2", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "新增3", null, null))));
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(50L);

        Long rowId = archiveService.archiveWindow(1L, 10);

        // 只归档 3 条新增消息（回填前缀 2 条不重复落库），沿用会话级 id=9
        assertThat(rowId).isEqualTo(50L);
        ArgumentCaptor<AgentConversation> convCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(convCaptor.capture());
        AgentConversation created = convCaptor.getAllValues().get(0);
        assertThat(created.getMessageCount()).isEqualTo(3);
        assertThat(created.getConversationId()).isEqualTo(9L);
        // 归档后热会话保留已归档回填前缀，新增已移除，前缀计数不变
        ArgumentCaptor<AgentSession> sessionCaptor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), sessionCaptor.capture());
        AgentSession saved = sessionCaptor.getValue();
        assertThat(saved.getMessages()).hasSize(2);
        assertThat(saved.getMessages().get(0).content()).isEqualTo("回填1");
        assertThat(saved.getArchivedPrefixCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("剩余归档 - 全部消息归档为一行并触发压缩、Redis 清空")
    void should_archiveRemaining_allMessages() {
        AgentSession session = sessionWithMessages(3);
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(60L);

        Long rowId = archiveService.archiveRemaining(1L);

        assertThat(rowId).isEqualTo(60L);
        ArgumentCaptor<AgentConversation> captor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getMessageCount()).isEqualTo(3);
        verify(messageRepository).saveAll(anyList());
        // 归档后热会话保存两次：doArchive 一次 + 清空 conversationId 一次（会话结束语义）
        ArgumentCaptor<AgentSession> sc = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService, times(2)).saveSession(eq(1L), sc.capture());
        assertThat(sc.getValue().getMessages()).isEmpty();
    }

    @Test
    @DisplayName("剩余归档 - 热会话仅剩已归档回填消息时不再重复建行（A→B→A 切换不虚高历史条数）")
    void should_archiveRemaining_skip_when_onlyBackfilled() {
        AgentSession session = new AgentSession();
        session.setConversationId(9L);
        session.setArchivedPrefixCount(2);
        session.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "回填问题", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "回填回答", null, null))));
        when(sessionService.getSession(1L)).thenReturn(session);

        Long rowId = archiveService.archiveRemaining(1L);

        // 无新增：不建行（回填消息已在 PG），热会话按会话结束语义清理
        assertThat(rowId).isNull();
        verify(conversationRepository, never()).save(any(AgentConversation.class));
        verify(messageRepository, never()).saveAll(anyList());
        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), captor.capture());
        AgentSession saved = captor.getValue();
        assertThat(saved.getMessages()).isEmpty();
        assertThat(saved.getConversationId()).isNull();
        assertThat(saved.getArchivedPrefixCount()).isZero();
    }

    @Test
    @DisplayName("剩余归档 - 只归档新增部分并沿用会话级 id（resume 后续对话切走不重复落库）")
    void should_archiveRemaining_onlyUnarchived_when_hasBackfilledPrefix() {
        AgentSession session = new AgentSession();
        session.setConversationId(9L);
        session.setArchivedPrefixCount(2);
        session.setMessages(new ArrayList<>(List.of(
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "回填1", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "回填2", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.USER, "新增1", null, null),
                new AgentSession.AgentMessageItem(AgentMessageRole.ASSISTANT, "新增2", null, null))));
        when(sessionService.getSession(1L)).thenReturn(session);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).tenantId(10L).build()));
        stubSaveAssigningIds(60L);

        Long rowId = archiveService.archiveRemaining(1L);

        // 只归档 2 条新增（回填前缀不重复落库），沿用会话级 id=9
        assertThat(rowId).isEqualTo(60L);
        ArgumentCaptor<AgentConversation> convCaptor = ArgumentCaptor.forClass(AgentConversation.class);
        verify(conversationRepository, atLeastOnce()).save(convCaptor.capture());
        AgentConversation created = convCaptor.getAllValues().get(0);
        assertThat(created.getMessageCount()).isEqualTo(2);
        assertThat(created.getConversationId()).isEqualTo(9L);
        // 热会话按会话结束语义清理（doArchive 一次 + 清空 conversationId 一次）
        ArgumentCaptor<AgentSession> sc = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService, times(2)).saveSession(eq(1L), sc.capture());
        AgentSession saved = sc.getValue();
        assertThat(saved.getMessages()).isEmpty();
        assertThat(saved.getConversationId()).isNull();
        assertThat(saved.getArchivedPrefixCount()).isZero();
    }

    // ==================== 列表（按会话级 conversation_id 分组） ====================

    @Test
    @DisplayName("列表 - 缺失字段回填默认值")
    void should_list_withDefaults_when_fieldsNull() {
        AgentConversation c = AgentConversation.builder()
                .id(1L).title(null).messageCount(null).status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0)).updatedAt(null).build();
        when(conversationRepository.findAllByUserIdAndStatusNot(1L, AgentConversationStatus.DELETED))
                .thenReturn(List.of(c));

        Page<Map<String, Object>> result = archiveService.list(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        Map<String, Object> row = result.getContent().get(0);
        assertThat(row.get("title")).isEqualTo("未命名对话");
        assertThat(row.get("messageCount")).isEqualTo(0);
        assertThat(row.get("updatedAt")).isEqualTo(c.getCreatedAt());
        assertThat(row.get("status")).isEqualTo(AgentConversationStatus.ARCHIVED);
    }

    @Test
    @DisplayName("列表 - 同一会话多条归档行合并为一条，messageCount 求和")
    void should_list_groupByConversation() {
        AgentConversation first = AgentConversation.builder()
                .id(1L).conversationId(1L).userId(1L).messageCount(2).title("搬家求助")
                .status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0)).updatedAt(LocalDateTime.of(2026, 8, 1, 10, 0)).build();
        AgentConversation second = AgentConversation.builder()
                .id(2L).conversationId(1L).userId(1L).messageCount(3).title(null)
                .status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 11, 0)).updatedAt(LocalDateTime.of(2026, 8, 1, 11, 0)).build();
        when(conversationRepository.findAllByUserIdAndStatusNot(1L, AgentConversationStatus.DELETED))
                .thenReturn(List.of(first, second));

        Page<Map<String, Object>> result = archiveService.list(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        Map<String, Object> row = result.getContent().get(0);
        assertThat(row.get("id")).isEqualTo(1L);
        assertThat(row.get("messageCount")).isEqualTo(5);
        // 标题取会话代表行（首次归档行 id==conversation_id）
        assertThat(row.get("title")).isEqualTo("搬家求助");
        assertThat(row.get("updatedAt")).isEqualTo(LocalDateTime.of(2026, 8, 1, 11, 0));
    }

    @Test
    @DisplayName("列表 - 排除当前正在进行的对话（热会话会话级 id 与归档会话一致时不展示，防切换到自己）")
    void should_list_excludeCurrentConversation() {
        // 会话 1：当前正在进行（已滑动窗口归档过，同会话同时存在于热会话与归档表）
        AgentConversation current = AgentConversation.builder()
                .id(1L).conversationId(1L).userId(1L).messageCount(5).title("当前对话")
                .status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0)).updatedAt(LocalDateTime.of(2026, 8, 1, 12, 0)).build();
        AgentConversation other = AgentConversation.builder()
                .id(2L).conversationId(2L).userId(1L).messageCount(3).title("其他对话")
                .status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 2, 10, 0)).updatedAt(LocalDateTime.of(2026, 8, 2, 11, 0)).build();
        when(conversationRepository.findAllByUserIdAndStatusNot(1L, AgentConversationStatus.DELETED))
                .thenReturn(List.of(current, other));
        // 当前热会话正在进行会话 id=1
        AgentSession session = new AgentSession();
        session.setConversationId(1L);
        when(sessionService.getSession(1L)).thenReturn(session);

        Page<Map<String, Object>> result = archiveService.list(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        Map<String, Object> row = result.getContent().get(0);
        assertThat(row.get("id")).isEqualTo(2L);
        assertThat(row.get("title")).isEqualTo("其他对话");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("列表 - 历史中仅当前对话时返回空（totalElements 反映排除后的数量）")
    void should_list_empty_when_onlyCurrentConversation() {
        AgentConversation current = AgentConversation.builder()
                .id(1L).conversationId(1L).userId(1L).messageCount(5).title("当前对话")
                .status(AgentConversationStatus.ARCHIVED)
                .createdAt(LocalDateTime.of(2026, 8, 1, 10, 0)).updatedAt(LocalDateTime.of(2026, 8, 1, 12, 0)).build();
        when(conversationRepository.findAllByUserIdAndStatusNot(1L, AgentConversationStatus.DELETED))
                .thenReturn(List.of(current));
        AgentSession session = new AgentSession();
        session.setConversationId(1L);
        when(sessionService.getSession(1L)).thenReturn(session);

        Page<Map<String, Object>> result = archiveService.list(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ==================== 恢复（按会话级 id） ====================

    @Test
    @DisplayName("恢复 - 归档会话不存在时抛业务异常，且不归档当前热会话（避免孤儿段/消息丢失）")
    void should_throw_when_conversationNotFound_onResume() {
        when(conversationRepository.findOwnedByConversationIdOrId(1L, 9L, 9L)).thenReturn(List.of());

        assertThatThrownBy(() -> archiveService.resume(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话不存在或无权访问");
        // 校验失败发生在归档之前：不触发当前热会话归档（不 saveSession），避免事务回滚后孤儿段残留
        verify(sessionService, never()).saveSession(anyLong(), any());
    }

    @Test
    @DisplayName("恢复 - 已软删会话抛业务异常，且不归档当前热会话（避免孤儿段/消息丢失）")
    void should_throw_when_conversationDeleted_onResume() {
        AgentConversation deleted = AgentConversation.builder()
                .id(9L).userId(1L).status(AgentConversationStatus.DELETED).build();
        when(conversationRepository.findOwnedByConversationIdOrId(1L, 9L, 9L)).thenReturn(List.of(deleted));

        assertThatThrownBy(() -> archiveService.resume(1L, 9L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("会话已删除");
        // 校验失败发生在归档之前：不触发当前热会话归档（不 saveSession），避免事务回滚后孤儿段残留
        verify(sessionService, never()).saveSession(anyLong(), any());
    }

    @Test
    @DisplayName("恢复 - 按会话级 id 合并全部归档行消息回填最近 N 轮，保留会话级 id")
    void should_resume_withRecentTurnsBackfill() {
        when(sessionService.getSession(1L)).thenReturn(null);
        // 同一会话两条归档行，共享会话级 id=9
        AgentConversation row1 = AgentConversation.builder()
                .id(5L).conversationId(9L).userId(1L).status(AgentConversationStatus.ARCHIVED).build();
        AgentConversation row2 = AgentConversation.builder()
                .id(6L).conversationId(9L).userId(1L).status(AgentConversationStatus.ARCHIVED).build();
        when(conversationRepository.findOwnedByConversationIdOrId(1L, 9L, 9L)).thenReturn(List.of(row1, row2));
        ReflectionTestUtils.setField(archiveService, "resumeTurns", 2);

        List<AgentMessage> msgs1 = List.of(
                AgentMessage.builder().id(1L).conversationId(5L).role(AgentMessageRole.USER).content("归档消息1").build(),
                AgentMessage.builder().id(2L).conversationId(5L).role(AgentMessageRole.USER).content("归档消息2").build());
        List<AgentMessage> msgs2 = List.of(
                AgentMessage.builder().id(3L).conversationId(6L).role(AgentMessageRole.USER).content("归档消息3").build(),
                AgentMessage.builder().id(4L).conversationId(6L).role(AgentMessageRole.USER).content("归档消息4").build(),
                AgentMessage.builder().id(5L).conversationId(6L).role(AgentMessageRole.USER).content("归档消息5").build(),
                AgentMessage.builder().id(6L).conversationId(6L).role(AgentMessageRole.USER).content("归档消息6").build());
        when(messageRepository.findByConversationIdOrderByIdAsc(5L)).thenReturn(msgs1);
        when(messageRepository.findByConversationIdOrderByIdAsc(6L)).thenReturn(msgs2);

        archiveService.resume(1L, 9L);

        ArgumentCaptor<AgentSession> captor = ArgumentCaptor.forClass(AgentSession.class);
        verify(sessionService).saveSession(eq(1L), captor.capture());
        AgentSession saved = captor.getValue();
        // 全部归档行消息合并按 id 升序回填最近 resumeTurns×2=4 条
        assertThat(saved.getMessages()).hasSize(4);
        assertThat(saved.getMessages().get(0).content()).isEqualTo("归档消息3");
        assertThat(saved.getMessages().get(3).content()).isEqualTo("归档消息6");
        // 会话级 id 保留，继续对话沿用同一会话；回填消息全部已在归档表，标记为已归档回填前缀
        assertThat(saved.getConversationId()).isEqualTo(9L);
        assertThat(saved.getArchivedPrefixCount()).isEqualTo(4);
    }

    // ==================== 软删（按会话级 id + 越权防护） ====================

    @Test
    @DisplayName("软删 - 按会话级 id 置 DELETED 并硬删衍生压缩段")
    void should_softDelete_onlyOwnedConversations() {
        AgentConversation row1 = AgentConversation.builder().id(5L).conversationId(9L).userId(1L)
                .status(AgentConversationStatus.ARCHIVED).build();
        AgentConversation row2 = AgentConversation.builder().id(6L).conversationId(9L).userId(1L)
                .status(AgentConversationStatus.ARCHIVED).build();
        when(conversationRepository.findOwnedBySessionIds(1L, List.of(9L))).thenReturn(List.of(row1, row2));
        AgentMemorySegment seg = AgentMemorySegment.builder().id(1L).conversationId(9L).build();
        when(memorySegmentRepository.findByConversationIdIn(Set.of(9L))).thenReturn(List.of(seg));

        int count = archiveService.softDelete(1L, List.of(9L));

        assertThat(count).isEqualTo(1);
        assertThat(row1.getStatus()).isEqualTo(AgentConversationStatus.DELETED);
        assertThat(row2.getStatus()).isEqualTo(AgentConversationStatus.DELETED);
        verify(conversationRepository).saveAll(List.of(row1, row2));
        // 衍生压缩段硬删（会话归属已按 userId 过滤确认）
        verify(memorySegmentRepository).deleteAll(List.of(seg));
    }

    @Test
    @DisplayName("软删 - 越权删除他人会话 id 被拒：归属查询为空不删任何行")
    void should_softDelete_reject_when_notOwned() {
        when(conversationRepository.findOwnedBySessionIds(1L, List.of(99L))).thenReturn(List.of());

        int count = archiveService.softDelete(1L, List.of(99L));

        assertThat(count).isZero();
        verify(conversationRepository, never()).saveAll(anyList());
        verify(memorySegmentRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("软删 - 空列表直接返回 0")
    void should_softDelete_zero_when_emptyList() {
        int count = archiveService.softDelete(1L, List.of());

        assertThat(count).isZero();
        verify(conversationRepository, never()).saveAll(anyList());
    }
}
