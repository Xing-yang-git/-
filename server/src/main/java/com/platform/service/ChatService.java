package com.platform.service;

import com.platform.model.dto.ChatMessageDTO;
import com.platform.model.dto.ChatSessionDTO;
import com.platform.model.entity.ChatMessage;
import com.platform.model.entity.ChatSession;
import com.platform.model.entity.User;
import com.platform.repository.ChatMessageRepository;
import com.platform.repository.ChatSessionRepository;
import com.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public ChatService(ChatSessionRepository chatSessionRepository,
                       ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    public List<ChatSessionDTO> getSessions(UUID userId) {
        List<ChatSession> sessions = chatSessionRepository.findByUser1IdOrUser2IdOrderByLastMessageAtDesc(userId, userId);

        return sessions.stream().map(session -> {
            UUID otherUserId = session.getUser1Id().equals(userId)
                    ? session.getUser2Id() : session.getUser1Id();
            User otherUser = userRepository.findById(otherUserId).orElse(null);

            long unreadCount = chatMessageRepository
                    .findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                    .filter(m -> !m.getSenderId().equals(userId) && !m.getIsRead())
                    .count();

            ChatMessage lastMessage = chatMessageRepository
                    .findTopBySessionIdOrderByCreatedAtDesc(session.getId());

            return ChatSessionDTO.builder()
                    .id(session.getId())
                    .postType(session.getPostType())
                    .postId(session.getPostId())
                    .otherUserId(otherUserId)
                    .otherUserName(otherUser != null ? otherUser.getName() : "未知用户")
                    .unreadCount((int) unreadCount)
                    .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
                    .lastMessageAt(lastMessage != null ? lastMessage.getCreatedAt() : session.getCreatedAt())
                    .createdAt(session.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<ChatMessageDTO> getMessages(UUID sessionId) {
        List<ChatMessage> messages = chatMessageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId);

        return messages.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public void markRead(UUID sessionId, UUID userId) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (ChatMessage message : messages) {
            if (!message.getSenderId().equals(userId)) {
                message.setIsRead(true);
            }
        }
        chatMessageRepository.saveAll(messages);
    }

    public UUID getOrCreateSession(String postType, UUID postId, UUID user1Id, UUID user2Id) {
        ChatSession session = chatSessionRepository
                .findByPostTypeAndPostIdAndUser1IdAndUser2Id(postType, postId, user1Id, user2Id)
                .orElse(chatSessionRepository
                        .findByPostTypeAndPostIdAndUser1IdAndUser2Id(postType, postId, user2Id, user1Id)
                        .orElse(null));

        if (session == null) {
            session = new ChatSession();
            session.setPostType(postType);
            session.setPostId(postId);
            session.setUser1Id(user1Id);
            session.setUser2Id(user2Id);
            session.setCreatedAt(LocalDateTime.now());
            session = chatSessionRepository.save(session);
        }

        return session.getId();
    }

    private ChatMessageDTO toDTO(ChatMessage message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);

        return ChatMessageDTO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderId(message.getSenderId())
                .senderName(sender != null ? sender.getName() : "未知用户")
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
