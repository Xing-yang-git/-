package com.platform.service;

import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.HelpRequestDTO;
import com.platform.model.dto.HelpResponseDTO;
import com.platform.model.dto.PageDTO;
import com.platform.model.entity.HelpApplication;
import com.platform.model.entity.HelpRequest;
import com.platform.model.entity.Notification;
import com.platform.model.entity.User;
import com.platform.repository.HelpApplicationRepository;
import com.platform.repository.HelpRequestRepository;
import com.platform.repository.NotificationRepository;
import com.platform.repository.RatingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HelpService 单元测试")
class HelpServiceTest {

    @Mock
    private HelpRequestRepository helpRequestRepository;
    @Mock
    private HelpApplicationRepository helpApplicationRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private HelpService helpService;

    private UUID userId;
    private UUID helperId;
    private UUID helpId;
    private UUID appId;
    private HelpRequest helpRequest;
    private HelpApplication application;
    private User user;
    private User helper;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        helperId = UUID.randomUUID();
        helpId = UUID.randomUUID();
        appId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .name("求助者")
                .userType("业主")
                .build();

        helper = User.builder()
                .id(helperId)
                .name("帮助者")
                .userType("业主")
                .build();

        helpRequest = HelpRequest.builder()
                .id(helpId)
                .userId(userId)
                .title("需要帮忙搬家具")
                .description("搬一个沙发")
                .category("搬家")
                .isUrgent(false)
                .status("online")
                .createdAt(LocalDateTime.now())
                .build();

        application = HelpApplication.builder()
                .id(appId)
                .helpId(helpId)
                .helperId(helperId)
                .note("我可以帮忙")
                .status("pending")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== publish ====================

    @Test
    @DisplayName("发布求助 - 正常发布求助成功")
    void should_publishHelp_when_validInput() {
        // Arrange
        HelpRequestDTO req = new HelpRequestDTO();
        req.setTitle("需要帮忙");
        req.setDescription("帮忙搬东西");
        req.setCategory("搬家");
        req.setIsUrgent(true);
        req.setTimeStart("2026-07-10 09:00");
        req.setTimeEnd("2026-07-10 12:00");

        when(helpRequestRepository.save(any(HelpRequest.class))).thenAnswer(inv -> {
            HelpRequest hr = inv.getArgument(0);
            hr.setId(helpId);
            return hr;
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.publish(userId, req);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("需要帮忙");
        assertThat(result.getStatus()).isEqualTo("online");
        assertThat(result.getIsUrgent()).isTrue();
        verify(helpRequestRepository).save(any(HelpRequest.class));
    }

    @Test
    @DisplayName("发布求助 - isUrgent为null时默认false")
    void should_defaultIsUrgentToFalse_when_null() {
        // Arrange
        HelpRequestDTO req = new HelpRequestDTO();
        req.setTitle("非紧急求助");
        req.setDescription("测试");
        req.setCategory("其他");

        when(helpRequestRepository.save(any(HelpRequest.class))).thenAnswer(inv -> {
            HelpRequest hr = inv.getArgument(0);
            hr.setId(helpId);
            return hr;
        });
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.publish(userId, req);

        // Assert
        assertThat(result.getIsUrgent()).isFalse();
    }

    // ==================== getHomeList ====================

    @Test
    @DisplayName("获取求助首页 - 正常返回分页列表")
    void should_returnHomeList_when_helpsExist() {
        // Arrange
        Page<HelpRequest> helpPage = new PageImpl<>(List.of(helpRequest),
                PageRequest.of(0, 10), 1);
        when(helpRequestRepository.findByStatus(eq("online"), any(PageRequest.class)))
                .thenReturn(helpPage);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        PageDTO<HelpResponseDTO> result = helpService.getHomeList(0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    // ==================== getDetail ====================

    @Test
    @DisplayName("获取求助详情 - 正常返回详情")
    void should_returnDetail_when_helpExists() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(ratingRepository.getAverageScore(userId)).thenReturn(4.0);
        when(helpRequestRepository.findByUserId(userId)).thenReturn(List.of(helpRequest));
        when(helpApplicationRepository.countByHelperIdAndStatus(userId, "approved")).thenReturn(5L);

        // Act
        HelpResponseDTO result = helpService.getDetail(helpId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRating()).isEqualTo(4.0);
        assertThat(result.getHelpCount()).isEqualTo(1);
        assertThat(result.getHelpedCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("获取求助详情 - 求助不存在时抛出异常")
    void should_throwException_when_helpNotFound() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> helpService.getDetail(helpId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("求助信息不存在");
    }

    // ==================== search ====================

    @Test
    @DisplayName("搜索求助 - 返回匹配结果")
    void should_searchHelp_when_keywordMatches() {
        // Arrange
        Page<HelpRequest> helpPage = new PageImpl<>(List.of(helpRequest),
                PageRequest.of(0, 10), 1);
        when(helpRequestRepository.findByStatusAndTitleContainingOrDescriptionContaining(
                eq("online"), eq("搬"), eq("搬"), any(PageRequest.class)))
                .thenReturn(helpPage);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        PageDTO<HelpResponseDTO> result = helpService.search("搬", 0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }

    // ==================== getMyPosts ====================

    @Test
    @DisplayName("获取我的求助 - 返回用户的求助列表")
    void should_returnMyPosts_when_userHasPosts() {
        // Arrange
        when(helpRequestRepository.findByUserId(userId)).thenReturn(List.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        List<HelpResponseDTO> result = helpService.getMyPosts(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("需要帮忙搬家具");
    }

    @Test
    @DisplayName("获取我的求助 - 无求助时返回空列表")
    void should_returnEmptyPosts_when_noPosts() {
        // Arrange
        when(helpRequestRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // Act
        List<HelpResponseDTO> result = helpService.getMyPosts(userId);

        // Assert
        assertThat(result).isEmpty();
    }

    // ==================== delist ====================

    @Test
    @DisplayName("下架求助 - 正常下架")
    void should_delistHelp_when_ownerOperates() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.delist(userId, helpId);

        // Assert
        assertThat(result.getStatus()).isEqualTo("offline");
        assertThat(helpRequest.getStatus()).isEqualTo("offline");
    }

    @Test
    @DisplayName("下架求助 - 非所有者操作时抛出异常")
    void should_throwException_when_delistNotOwner() {
        // Arrange
        UUID otherId = UUID.randomUUID();
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.delist(otherId, helpId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("无权操作该求助");
    }

    // ==================== apply ====================

    @Test
    @DisplayName("申请帮助 - 正常申请成功")
    void should_applyHelp_when_validInput() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpApplicationRepository.save(any(HelpApplication.class))).thenAnswer(inv -> {
            HelpApplication app = inv.getArgument(0);
            app.setId(appId);
            return app;
        });
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.apply(helperId, helpId, "我可以帮忙");

        // Assert
        assertThat(result).isNotNull();
        verify(helpApplicationRepository).save(any(HelpApplication.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("申请帮助 - 求助已关闭时抛出异常")
    void should_throwException_when_helpClosed() {
        // Arrange
        helpRequest.setStatus("offline");
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.apply(helperId, helpId, "note"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("该求助已关闭");
    }

    @Test
    @DisplayName("申请帮助 - 申请自己的求助时抛出异常")
    void should_throwException_when_applyingOwnHelp() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.apply(userId, helpId, "note"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不能申请自己的求助");
    }

    // ==================== approveReject ====================

    @Test
    @DisplayName("审批帮助 - 通过申请并更新状态")
    void should_approveHelp_when_validApproval() {
        // Arrange
        ApproveRequest req = new ApproveRequest();
        req.setApproved(true);

        when(helpApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpApplicationRepository.save(any(HelpApplication.class))).thenReturn(application);
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.approveReject(userId, appId, req);

        // Assert
        assertThat(application.getStatus()).isEqualTo("approved");
        assertThat(helpRequest.getStatus()).isEqualTo("helping");
    }

    @Test
    @DisplayName("审批帮助 - 非所有者操作时抛出异常")
    void should_throwException_when_approveNotOwner() {
        // Arrange
        ApproveRequest req = new ApproveRequest();
        req.setApproved(true);
        UUID otherId = UUID.randomUUID();

        when(helpApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.approveReject(otherId, appId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("无权操作该申请");
    }

    @Test
    @DisplayName("审批帮助 - 申请已被处理时抛出异常")
    void should_throwException_when_applicationAlreadyProcessed() {
        // Arrange
        application.setStatus("approved");
        ApproveRequest req = new ApproveRequest();
        req.setApproved(true);

        when(helpApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.approveReject(userId, appId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("该申请已被处理，无法重复操作");
    }

    // ==================== completeHelp ====================

    @Test
    @DisplayName("完成帮助 - 正常完成帮助")
    void should_completeHelp_when_validCompletion() {
        // Arrange
        application.setStatus("approved");

        when(helpApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpApplicationRepository.save(any(HelpApplication.class))).thenReturn(application);
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.completeHelp(userId, appId);

        // Assert
        assertThat(application.getStatus()).isEqualTo("completed");
        assertThat(helpRequest.getStatus()).isEqualTo("completed");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("完成帮助 - 只能完成已批准的申请")
    void should_throwException_when_notApprovedApplication() {
        // Arrange
        application.setStatus("pending");

        when(helpApplicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));

        // Act & Assert
        assertThatThrownBy(() -> helpService.completeHelp(userId, appId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("只能完成已批准的帮助申请");
    }

    // ==================== update ====================

    @Test
    @DisplayName("更新求助 - 正常更新求助信息")
    void should_updateHelp_when_validUpdate() {
        // Arrange
        HelpRequestDTO req = new HelpRequestDTO();
        req.setTitle("更新后的求助标题");
        req.setDescription("更新描述");

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.update(userId, helpId, req);

        // Assert
        assertThat(result.getTitle()).isEqualTo("更新后的求助标题");
    }

    @Test
    @DisplayName("更新求助 - completed状态自动恢复为online")
    void should_autoRelist_when_statusCompleted() {
        // Arrange
        helpRequest.setStatus("completed");
        HelpRequestDTO req = new HelpRequestDTO();
        req.setTitle("重新发布");

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        HelpResponseDTO result = helpService.update(userId, helpId, req);

        // Assert
        assertThat(result.getStatus()).isEqualTo("online");
    }

    // ==================== getMyApplications ====================

    @Test
    @DisplayName("获取我的帮助申请 - 返回申请列表")
    void should_returnMyApplications_when_hasApplications() {
        // Arrange
        when(helpApplicationRepository.findByHelperId(helperId)).thenReturn(List.of(application));
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        List<HelpResponseDTO> result = helpService.getMyApplications(helperId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApplicationStatus()).isEqualTo("pending");
        assertThat(result.get(0).getApplicationId()).isEqualTo(appId);
    }

    // ==================== getPendingApprovals ====================

    @Test
    @DisplayName("获取待审批 - 返回待审批帮助申请")
    void should_returnPendingApprovals_when_hasPending() {
        // Arrange
        when(helpRequestRepository.findByUserId(userId)).thenReturn(List.of(helpRequest));
        when(helpApplicationRepository.findByHelpIdAndStatus(helpId, "pending"))
                .thenReturn(List.of(application));
        when(userRepository.findById(helperId)).thenReturn(Optional.of(helper));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        List<HelpResponseDTO> result = helpService.getPendingApprovals(userId);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHelperName()).isEqualTo("帮助者");
    }
}
