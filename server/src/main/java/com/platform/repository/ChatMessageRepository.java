package com.platform.repository;

import com.platform.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    ChatMessage findTopBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    long countBySessionIdAndSenderIdNotAndIsReadFalse(UUID sessionId, UUID senderId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
