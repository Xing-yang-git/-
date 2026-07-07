package com.platform.repository;

import com.platform.model.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    List<ChatSession> findByUser1IdOrUser2IdOrderByLastMessageAtDesc(UUID user1Id, UUID user2Id);

    Optional<ChatSession> findByPostTypeAndPostIdAndUser1IdAndUser2Id(
            String postType, UUID postId, UUID user1Id, UUID user2Id);
}
