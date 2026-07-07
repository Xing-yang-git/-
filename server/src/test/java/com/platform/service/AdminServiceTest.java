package com.platform.service;

import com.platform.model.dto.*;
import com.platform.model.entity.*;
import com.platform.repository.*;
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
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService 单元测试")
class AdminServiceTest {

    @Mock private IdleItemRepository idleItemRepository;
    @Mock private HelpRequestRepository helpRequestRepository;
    @Mock private BorrowRequestRepository borrowRequestRepository;
    @Mock private HelpApplicationRepository helpApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private VerificationRepository verificationRepository;
    @Mock private OperationLogRepository operationLogRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RatingRepository ratingRepository;

    @InjectMocks
    private AdminService adminService;

    private UUID adminId;
    private UUID userId;
    private UUID itemId;
    private UUID helpId;
    private User user;
    private IdleItem idleItem;
    private HelpRequest helpRequest;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        helpId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .name("测试用户")
                .userType("业主")
                .authStatus("approved")
                .createdAt(LocalDateTime.now())
                .build();

        idleItem = IdleItem.builder()
                .id(itemId)
                .userId(userId)
                .title("闲置物品")
                .description("物品描述")
                .postType("LEND")
                .category("数码")
                .status("online")
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();

        helpRequest = HelpRequest.builder()
                .id(helpId)
                .userId(userId)
                .title("求助信息")
                .description("求助描述")
                .category("搬家")
                .isUrgent(false)
                .status("online")
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ==================== getDashboard ====================

    @Test
    @DisplayName("获取仪表盘 - 正常返回统计数据")
    void should_returnDashboard_when_dataExists() {
        // Arrange
        when(idleItemRepository.findAll()).thenReturn(List.of(idleItem));
        when(helpRequestRepository.findAll()).thenReturn(List.of(helpRequest));
        when(borrowRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(userRepository.findAll()).thenReturn(List.of(user));

        // Act
        DashboardDTO result = adminService.getDashboard();

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getOnlineIdleCount()).isEqualTo(1);
        assertThat(result.getOnlineHelpCount()).isEqualTo(1);
        assertThat(result.getItemStats()).isNotNull();
        assertThat(result.getCategoryStats()).isNotNull();
    }

    // ==================== getAudits ====================

    @Test
    @DisplayName("获取审核列表 - 按状态过滤返回分页数据")
    void should_returnAudits_when_filteredByStatus() {
        // Arrange
        Page<User> userPage = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        when(userRepository.findByAuthStatus(eq("pending"), any(PageRequest.class)))
                .thenReturn(userPage);

        // Act
        PageDTO<UserDTO> result = adminService.getAudits("pending", 0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("获取审核列表 - 空状态时排除 registering 用户")
    void should_returnAllNonRegistering_when_statusEmpty() {
        // Arrange
        Page<User> userPage = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        when(userRepository.findByAuthStatusNot(eq("registering"), any(PageRequest.class)))
                .thenReturn(userPage);

        // Act
        PageDTO<UserDTO> result = adminService.getAudits(null, 0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
    }

    // ==================== getAuditCounts ====================

    @Test
    @DisplayName("获取审核计数 - 返回各状态的数量")
    void should_returnAuditCounts_when_called() {
        // Arrange
        when(userRepository.countByAuthStatus("pending")).thenReturn(5L);
        when(userRepository.countByAuthStatus("approved")).thenReturn(10L);
        when(userRepository.countByAuthStatus("rejected")).thenReturn(2L);
        when(userRepository.countByAuthStatusNot("registering")).thenReturn(17L);

        // Act
        Map<String, Long> result = adminService.getAuditCounts();

        // Assert
        assertThat(result).containsEntry("pending", 5L);
        assertThat(result).containsEntry("approved", 10L);
        assertThat(result).containsEntry("rejected", 2L);
        assertThat(result).containsEntry("all", 17L);
    }

    // ==================== auditUser ====================

    @Test
    @DisplayName("审核用户 - 通过审核")
    void should_approveUser_when_approved() {
        // Arrange
        AuditRequest req = new AuditRequest();
        req.setApproved(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        Map<String, Object> result = adminService.auditUser(adminId, userId, req);

        // Assert
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("审核通过");
        assertThat(user.getAuthStatus()).isEqualTo("approved");
        assertThat(user.getRejectReason()).isNull();
        verify(operationLogRepository).save(any(OperationLog.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("审核用户 - 拒绝审核并记录原因")
    void should_rejectUser_when_rejected() {
        // Arrange
        AuditRequest req = new AuditRequest();
        req.setApproved(false);
        req.setReason("资料不完整");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        Map<String, Object> result = adminService.auditUser(adminId, userId, req);

        // Assert
        assertThat(result.get("message")).isEqualTo("已拒绝");
        assertThat(user.getAuthStatus()).isEqualTo("rejected");
        assertThat(user.getRejectReason()).isEqualTo("资料不完整");
    }

    @Test
    @DisplayName("审核用户 - 用户不存在时抛出异常")
    void should_throwException_when_userNotFound() {
        // Arrange
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> adminService.auditUser(adminId, userId, new AuditRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户不存在");
    }

    // ==================== getContentCounts ====================

    @Test
    @DisplayName("获取内容计数 - 返回各状态的数量")
    void should_returnContentCounts() {
        // Arrange
        when(idleItemRepository.countByStatus("online")).thenReturn(10L);
        when(helpRequestRepository.countByStatus("online")).thenReturn(5L);
        when(idleItemRepository.countByStatus("borrowing")).thenReturn(3L);
        when(helpRequestRepository.countByStatus("helping")).thenReturn(2L);
        when(idleItemRepository.countByStatus("completed")).thenReturn(8L);
        when(helpRequestRepository.countByStatus("completed")).thenReturn(4L);
        when(idleItemRepository.countByStatus("deleted")).thenReturn(1L);
        when(helpRequestRepository.countByStatus("deleted")).thenReturn(1L);

        // Act
        Map<String, Long> result = adminService.getContentCounts();

        // Assert
        assertThat(result).containsEntry("showing", 15L);
        assertThat(result).containsEntry("progressing", 5L);
        assertThat(result).containsEntry("completed", 12L);
        assertThat(result).containsEntry("violation", 2L);
        assertThat(result).containsEntry("all", 34L);
    }

    // ==================== getContentDetail ====================

    @Test
    @DisplayName("获取内容详情 - idle类型返回详情")
    void should_returnIdleDetail_when_typeIdle() {
        // Arrange
        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        ContentItemDTO result = adminService.getContentDetail(itemId, "idle");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("idle");
        assertThat(result.getTitle()).isEqualTo("闲置物品");
    }

    @Test
    @DisplayName("获取内容详情 - help类型返回详情")
    void should_returnHelpDetail_when_typeHelp() {
        // Arrange
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        ContentItemDTO result = adminService.getContentDetail(helpId, "help");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("help");
        assertThat(result.getTitle()).isEqualTo("求助信息");
    }

    @Test
    @DisplayName("获取内容详情 - 不支持的类型抛出异常")
    void should_throwException_when_unsupportedType() {
        // Act & Assert
        assertThatThrownBy(() -> adminService.getContentDetail(itemId, "unknown"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的类型，请使用 idle 或 help");
    }

    // ==================== removeContent ====================

    @Test
    @DisplayName("删除内容 - 删除idle类型内容")
    void should_removeIdleContent_when_validRequest() {
        // Arrange
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("idle");
        req.setReasons(List.of("违规内容"));
        req.setCustomReason("包含广告信息");

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(idleItemRepository.save(any(IdleItem.class))).thenReturn(idleItem);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        Map<String, Object> result = adminService.removeContent(adminId, itemId, req);

        // Assert
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("内容已删除");
        assertThat(idleItem.getStatus()).isEqualTo("deleted");
        assertThat(idleItem.getViolationType()).isEqualTo("违规内容");
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("删除内容 - 删除help类型内容")
    void should_removeHelpContent_when_validRequest() {
        // Arrange
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("help");
        req.setReasons(List.of("虚假信息"));

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        Map<String, Object> result = adminService.removeContent(adminId, helpId, req);

        // Assert
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(helpRequest.getStatus()).isEqualTo("deleted");
    }

    @Test
    @DisplayName("删除内容 - reasons为null时默认使用'违规'")
    void should_useDefaultViolationType_when_reasonsNull() {
        // Arrange
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("idle");
        req.setCustomReason("自定义原因");

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(idleItemRepository.save(any(IdleItem.class))).thenReturn(idleItem);
        when(notificationRepository.save(any(Notification.class))).thenReturn(new Notification());
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        adminService.removeContent(adminId, itemId, req);

        // Assert
        assertThat(idleItem.getViolationType()).isEqualTo("违规");
    }

    @Test
    @DisplayName("删除内容 - 不支持的目标类型抛出异常")
    void should_throwException_when_unsupportedTargetType() {
        // Arrange
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("unknown");

        // Act & Assert
        assertThatThrownBy(() -> adminService.removeContent(adminId, itemId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的目标类型");
    }

    // ==================== proxyPublishIdle ====================

    @Test
    @DisplayName("代发闲置 - 正常代发闲置物品")
    void should_proxyPublishIdle_when_validInput() {
        // Arrange
        IdleItemRequest req = new IdleItemRequest();
        req.setUserId(userId);
        req.setTitle("代发物品");
        req.setDescription("测试代发");
        req.setPostType("LEND");
        req.setCategory("数码");
        req.setPrice(java.math.BigDecimal.TEN);

        when(idleItemRepository.save(any(IdleItem.class))).thenAnswer(inv -> {
            IdleItem item = inv.getArgument(0);
            item.setId(itemId);
            return item;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        IdleItemDTO result = adminService.proxyPublishIdle(adminId, req);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("代发物品");
        assertThat(result.getStatus()).isEqualTo("online");
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("代发闲置 - userId为null时使用adminId")
    void should_useAdminId_when_userIdNull() {
        // Arrange
        IdleItemRequest req = new IdleItemRequest();
        req.setTitle("管理员代发");
        req.setDescription("测试");
        req.setPostType("LEND");
        req.setCategory("数码");

        when(idleItemRepository.save(any(IdleItem.class))).thenAnswer(inv -> {
            IdleItem item = inv.getArgument(0);
            item.setId(itemId);
            return item;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        IdleItemDTO result = adminService.proxyPublishIdle(adminId, req);

        // Assert
        assertThat(result.getUserId()).isEqualTo(adminId);
    }

    // ==================== proxyPublishHelp ====================

    @Test
    @DisplayName("代发求助 - 正常代发求助")
    void should_proxyPublishHelp_when_validInput() {
        // Arrange
        HelpRequestDTO req = new HelpRequestDTO();
        req.setUserId(userId);
        req.setTitle("代发求助");
        req.setDescription("测试代发求助");
        req.setCategory("搬家");
        req.setIsUrgent(true);

        when(helpRequestRepository.save(any(HelpRequest.class))).thenAnswer(inv -> {
            HelpRequest hr = inv.getArgument(0);
            hr.setId(helpId);
            return hr;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // Act
        HelpResponseDTO result = adminService.proxyPublishHelp(adminId, req);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("代发求助");
        assertThat(result.getStatus()).isEqualTo("online");
        assertThat(result.getIsUrgent()).isTrue();
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    // ==================== getRecords ====================

    @Test
    @DisplayName("获取记录 - borrow类型返回借入记录")
    void should_returnBorrowRecords_when_typeBorrow() {
        // Arrange
        BorrowRequest br = BorrowRequest.builder()
                .id(UUID.randomUUID())
                .idleId(itemId)
                .borrowerId(userId)
                .status("returned")
                .createdAt(LocalDateTime.now())
                .build();

        when(borrowRequestRepository.findByStatus("returned")).thenReturn(List.of(br));
        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        PageDTO<Map<String, Object>> result = adminService.getRecords("borrow", 0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).get("type")).isEqualTo("borrow");
    }

    @Test
    @DisplayName("获取记录 - 不支持的类型抛出异常")
    void should_throwException_when_unsupportedRecordType() {
        // Act & Assert
        assertThatThrownBy(() -> adminService.getRecords("unknown", 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的类型，请使用 borrow 或 help");
    }

    // ==================== getOperationLogs ====================

    @Test
    @DisplayName("获取操作日志 - 返回分页日志")
    void should_returnOperationLogs_when_logsExist() {
        // Arrange
        OperationLog log = OperationLog.builder()
                .id(UUID.randomUUID())
                .adminId(adminId)
                .action("approve_user")
                .targetType("user")
                .targetId(userId)
                .detail("审核通过")
                .createdAt(LocalDateTime.now())
                .build();

        Page<OperationLog> logPage = new PageImpl<>(List.of(log), PageRequest.of(0, 10), 1);
        when(operationLogRepository.findAll(any(PageRequest.class))).thenReturn(logPage);
        when(userRepository.findById(adminId)).thenReturn(Optional.of(
                User.builder().id(adminId).name("管理员").build()));

        // Act
        PageDTO<OperationLogDTO> result = adminService.getOperationLogs(0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("approve_user");
        assertThat(result.getContent().get(0).getAdminName()).isEqualTo("管理员");
    }

    // ==================== getBuildings ====================

    @Test
    @DisplayName("获取楼栋列表 - 返回所有楼栋")
    void should_returnBuildings_when_called() {
        // Arrange
        Building building = Building.builder()
                .id(UUID.randomUUID())
                .name("3栋")
                .build();
        when(buildingRepository.findAll()).thenReturn(List.of(building));

        // Act
        List<Map<String, Object>> result = adminService.getBuildings();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("3栋");
    }

    // ==================== exportData ====================

    @Test
    @DisplayName("导出数据 - idle类型导出闲置物品数据")
    void should_exportIdleData_when_typeIdle() {
        // Arrange
        when(idleItemRepository.findAll()).thenReturn(List.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        List<Map<String, Object>> result = adminService.exportData("idle");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("type")).isEqualTo("idle");
        assertThat(result.get(0).get("title")).isEqualTo("闲置物品");
    }

    @Test
    @DisplayName("导出数据 - help类型导出求助数据")
    void should_exportHelpData_when_typeHelp() {
        // Arrange
        when(helpRequestRepository.findAll()).thenReturn(List.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // Act
        List<Map<String, Object>> result = adminService.exportData("help");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("type")).isEqualTo("help");
    }

    @Test
    @DisplayName("导出数据 - 未知类型返回空列表")
    void should_returnEmpty_when_unknownExportType() {
        // Act
        List<Map<String, Object>> result = adminService.exportData("unknown");

        // Assert
        assertThat(result).isEmpty();
    }
}
