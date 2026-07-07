package com.platform.service;

import com.platform.model.dto.ChatMessageDTO;
import com.platform.model.dto.ChatSessionDTO;
import com.platform.model.entity.ChatMessage;
import com.platform.model.entity.ChatSession;
import com.platform.model.entity.User;
import com.platform.repository.ChatMessageRepository;
import com.platform.repository.ChatSessionRepository;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService 单元测试")
class ChatServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID otherUserId;
    private UUID sessionId;
    private UUID postId;
    private ChatSession session;
    private User otherUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        postId = UUID.randomUUID();

        otherUser = User.builder()
                .id(otherUserId)
                .name("李四")
                .build();

        session = ChatSession.builder()
                .id(sessionId)
                .postType("idle")
                .postId(postId)
                .user1Id(userId)
                .user2Id(otherUserId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取会话列表 - 正常返回用户的所有会话")
    void should_returnSessions_when_userHasSessions() {
        // Arrange
        ChatMessage lastMsg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .senderId(otherUserId)
                .content("你好")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(chatSessionRepository.findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId))
                .thenReturn(List.of(session));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(lastMsg));
        when(chatMessageRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId))
                .thenReturn(lastMsg);

        // Act
        List<ChatSessionDTO> result = chatService.getSessions(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOtherUserId()).isEqualTo(otherUserId);
        assertThat(result.get(0).getOtherUserName()).isEqualTo("李四");
        assertThat(result.get(0).getUnreadCount()).isEqualTo(1);
        assertThat(result.get(0).getLastMessage()).isEqualTo("你好");
        verify(chatSessionRepository).findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId);
    }

    @Test
    @DisplayName("获取会话列表 - 用户没有会话时返回空列表")
    void should_returnEmptySessions_when_userHasNoSessions() {
        // Arrange
        when(chatSessionRepository.findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId))
                .thenReturn(Collections.emptyList());

        // Act
        List<ChatSessionDTO> result = chatService.getSessions(userId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("获取会话列表 - 识别对方用户ID正确(user1=当前用户)")
    void should_identifyOtherUser_when_currentUserIsUser1() {
        // Arrange
        when(chatSessionRepository.findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId))
                .thenReturn(List.of(session));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId))
                .thenReturn(null);

        // Act
        List<ChatSessionDTO> result = chatService.getSessions(userId);

        // Assert
        assertThat(result.get(0).getOtherUserId()).isEqualTo(otherUserId);
    }

    @Test
    @DisplayName("获取会话列表 - 识别对方用户ID正确(user2=当前用户)")
    void should_identifyOtherUser_when_currentUserIsUser2() {
        // Arrange
        ChatSession reversedSession = ChatSession.builder()
                .id(sessionId)
                .postType("idle")
                .postId(postId)
                .user1Id(otherUserId)
                .user2Id(userId)
                .createdAt(LocalDateTime.now())
                .build();

        when(chatSessionRepository.findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId))
                .thenReturn(List.of(reversedSession));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());
        when(chatMessageRepository.findTopBySessionIdOrderByCreatedAtDesc(sessionId))
                .thenReturn(null);

        // Act
        List<ChatSessionDTO> result = chatService.getSessions(userId);

        // Assert
        assertThat(result.get(0).getOtherUserId()).isEqualTo(otherUserId);
    }

    @Test
    @DisplayName("获取消息列表 - 正常返回会话消息")
    void should_returnMessages_when_sessionHasMessages() {
        // Arrange
        ChatMessage msg = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .senderId(userId)
                .content("测试消息")
                .isRead(true)
                .createdAt(LocalDateTime.now())
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(msg));
        when(userRepository.findById(userId)).thenReturn(Optional.of(otherUser));

        // Act
        List<ChatMessageDTO> result = chatService.getMessages(sessionId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("测试消息");
        assertThat(result.get(0).getSenderName()).isEqualTo("李四");
    }

    @Test
    @DisplayName("获取消息列表 - 会话没有消息时返回空列表")
    void should_returnEmptyMessages_when_sessionHasNoMessages() {
        // Arrange
        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(Collections.emptyList());

        // Act
        List<ChatMessageDTO> result = chatService.getMessages(sessionId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("标记已读 - 将非发送者的消息标记为已读")
    void should_markMessagesAsRead_when_otherUserSentThem() {
        // Arrange
        ChatMessage msg1 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .senderId(otherUserId)
                .content("消息1")
                .isRead(false)
                .build();
        ChatMessage msg2 = ChatMessage.builder()
                .id(UUID.randomUUID())
                .sessionId(sessionId)
                .senderId(userId)
                .content("消息2")
                .isRead(true)
                .build();

        when(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId))
                .thenReturn(List.of(msg1, msg2));

        // Act
        chatService.markRead(sessionId, userId);

        // Assert
        assertThat(msg1.getIsRead()).isTrue();
        assertThat(msg2.getIsRead()).isTrue(); // already true, unchanged
        verify(chatMessageRepository).saveAll(any());
    }

    @Test
    @DisplayName("获取或创建会话 - 会话已存在时返回已有会话ID")
    void should_returnExistingSession_when_sessionAlreadyExists() {
        // Arrange
        when(chatSessionRepository.findByPostTypeAndPostIdAndUser1IdAndUser2Id("idle", postId, userId, otherUserId))
                .thenReturn(Optional.of(session));

        // Act
        UUID result = chatService.getOrCreateSession("idle", postId, userId, otherUserId);

        // Assert
        assertThat(result).isEqualTo(sessionId);
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("获取或创建会话 - 反向查找到已有会话(reversed user order)")
    void should_returnExistingSession_when_foundInReverseOrder() {
        // Arrange
        when(chatSessionRepository.findByPostTypeAndPostIdAndUser1IdAndUser2Id("idle", postId, userId, otherUserId))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.findByPostTypeAndPostIdAndUser1IdAndUser2Id("idle", postId, otherUserId, userId))
                .thenReturn(Optional.of(session));

        // Act
        UUID result = chatService.getOrCreateSession("idle", postId, userId, otherUserId);

        // Assert
        assertThat(result).isEqualTo(sessionId);
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("获取或创建会话 - 会话不存在时创建新会话")
    void should_createNewSession_when_sessionDoesNotExist() {
        // Arrange
        when(chatSessionRepository.findByPostTypeAndPostIdAndUser1IdAndUser2Id("idle", postId, userId, otherUserId))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.findByPostTypeAndPostIdAndUser1IdAndUser2Id("idle", postId, otherUserId, userId))
                .thenReturn(Optional.empty());
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(sessionId);
            return s;
        });

        // Act
        UUID result = chatService.getOrCreateSession("idle", postId, userId, otherUserId);

        // Assert
        assertThat(result).isEqualTo(sessionId);
        verify(chatSessionRepository).save(any(ChatSession.class));
    }
}
