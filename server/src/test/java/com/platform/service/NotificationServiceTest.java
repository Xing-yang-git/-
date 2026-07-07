package com.platform.service;

import com.platform.model.dto.NotificationDTO;
import com.platform.model.entity.Notification;
import com.platform.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 单元测试")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;
    private UUID relatedId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        relatedId = UUID.randomUUID();

        notification = Notification.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type("system")
                .title("测试通知")
                .content("这是一条测试通知")
                .relatedId(relatedId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("获取通知列表 - 正常返回用户的通知列表")
    void should_returnNotifications_when_userHasNotifications() {
        // Arrange
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(notification));

        // Act
        List<NotificationDTO> result = notificationService.getNotifications(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("测试通知");
        assertThat(result.get(0).getContent()).isEqualTo("这是一条测试通知");
        assertThat(result.get(0).getType()).isEqualTo("system");
        assertThat(result.get(0).getRelatedId()).isEqualTo(relatedId);
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("获取通知列表 - 用户没有通知时返回空列表")
    void should_returnEmptyList_when_userHasNoNotifications() {
        // Arrange
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Collections.emptyList());

        // Act
        List<NotificationDTO> result = notificationService.getNotifications(userId);

        // Assert
        assertThat(result).isEmpty();
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    @DisplayName("获取未读数量 - 返回正确的未读计数")
    void should_returnUnreadCount_when_userHasUnreadNotifications() {
        // Arrange
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(5L);

        // Act
        long count = notificationService.getUnreadCount(userId);

        // Assert
        assertThat(count).isEqualTo(5L);
        verify(notificationRepository).countByUserIdAndIsReadFalse(userId);
    }

    @Test
    @DisplayName("获取未读数量 - 用户没有未读通知时返回0")
    void should_returnZero_when_userHasNoUnreadNotifications() {
        // Arrange
        when(notificationRepository.countByUserIdAndIsReadFalse(userId)).thenReturn(0L);

        // Act
        long count = notificationService.getUnreadCount(userId);

        // Assert
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("全部标为已读 - 调用Repository的markAllRead方法")
    void should_markAllRead_when_called() {
        // Arrange
        doNothing().when(notificationRepository).markAllRead(userId);

        // Act
        notificationService.markAllRead(userId);

        // Assert
        verify(notificationRepository).markAllRead(userId);
    }

    @Test
    @DisplayName("创建通知 - 正常创建并返回DTO")
    void should_createNotification_when_validInput() {
        // Arrange
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        // Act
        NotificationDTO result = notificationService.create(userId, "system", "新通知", "内容", relatedId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("新通知");
        assertThat(result.getContent()).isEqualTo("内容");
        assertThat(result.getType()).isEqualTo("system");
        assertThat(result.getRelatedId()).isEqualTo(relatedId);
        assertThat(result.getIsRead()).isFalse();
        assertThat(result.getCreatedAt()).isNotNull();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getIsRead()).isFalse();
    }
}
