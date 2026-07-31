package com.platform.repository;

import com.platform.model.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId")
    void markAllRead(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    /** 删除指定用户、类型、关联ID的所有通知（调用方应在删除后立即创建新通知，以此保证同一 relatedId 只保留最新一条） */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId AND n.type = :type AND n.relatedId = :relatedId")
    void deleteByUserIdAndTypeAndRelatedId(@Param("userId") Long userId,
                                           @Param("type") String type,
                                           @Param("relatedId") Long relatedId);

    /** 检查指定用户、类型、关联ID的通知是否已存在（用于供需匹配去重） */
    boolean existsByUserIdAndTypeAndRelatedId(Long userId, String type, Long relatedId);
}
