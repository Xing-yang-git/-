package com.platform.service;

import com.platform.model.dto.NotificationDTO;
import com.platform.model.entity.Notification;
import com.platform.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<NotificationDTO> getNotifications(Long userId) {
        List<Notification> notifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        return notifications.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    public NotificationDTO create(Long userId, String type, String title, String content, Long relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notification = notificationRepository.save(notification);

        return toDTO(notification);
    }

    private NotificationDTO toDTO(Notification notification) {
        return NotificationDTO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .relatedId(notification.getRelatedId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
