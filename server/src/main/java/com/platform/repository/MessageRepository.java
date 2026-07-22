package com.platform.repository;

import com.platform.model.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 游标分页拉取会话历史（按 id 倒序，取最新的 N 条）。
     */
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId ORDER BY m.id DESC")
    List<Message> findLatestBySession(@Param("sessionId") String sessionId, Pageable pageable);

    /**
     * 游标分页：取指定 id 之前的更早消息。
     */
    @Query("SELECT m FROM Message m WHERE m.sessionId = :sessionId AND m.id < :beforeId ORDER BY m.id DESC")
    List<Message> findBeforeBySession(@Param("sessionId") String sessionId, @Param("beforeId") Long beforeId, Pageable pageable);

    /**
     * 查询用户参与的会话列表（消息列表页使用：该用户发了或收了消息的 session_id 去重）。
     */
    @Query("SELECT DISTINCT m.sessionId FROM Message m WHERE m.fromUserId = :userId OR m.toUserId = :userId")
    List<String> findDistinctSessionsByUser(@Param("userId") Long userId);

    /**
     * 按用户对查询最新消息（不限定 sessionId），用于统一会话后的回退查找。
     */
    @Query("SELECT m FROM Message m WHERE (m.fromUserId = :uid1 AND m.toUserId = :uid2) OR (m.fromUserId = :uid2 AND m.toUserId = :uid1) ORDER BY m.id DESC")
    List<Message> findLatestBetweenUsers(@Param("uid1") Long uid1, @Param("uid2") Long uid2, Pageable pageable);

    /**
     * 按用户对 + 游标查询更早消息。
     */
    @Query("SELECT m FROM Message m WHERE ((m.fromUserId = :uid1 AND m.toUserId = :uid2) OR (m.fromUserId = :uid2 AND m.toUserId = :uid1)) AND m.id < :beforeId ORDER BY m.id DESC")
    List<Message> findBeforeBetweenUsers(@Param("uid1") Long uid1, @Param("uid2") Long uid2, @Param("beforeId") Long beforeId, Pageable pageable);
}
