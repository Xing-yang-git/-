package com.platform.service;

import com.platform.model.dto.*;
import com.platform.model.entity.*;
import com.platform.repository.*;
import com.platform.websocket.ChatWebSocketHandler;
import com.platform.common.BizStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
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
    @Mock private OperationLogRepository operationLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;
    @Mock private ChatWebSocketHandler chatWebSocketHandler;

    @InjectMocks
    private AdminService adminService;

    private Long adminId;
    private Long userId;
    private Long itemId;
    private Long helpId;
    private Long tenantId;
    private User admin;
    private User user;
    private IdleItem idleItem;
    private HelpRequest helpRequest;

    @BeforeEach
    void setUp() {
        // 各实体使用不同的 Long 字面量，保证测试内 ID 互不冲突
        adminId = 1L;
        userId = 2L;
        itemId = 100L;
        helpId = 200L;
        tenantId = 10L;

        // 管理员账号：多数方法先通过 adminId 解析所属小区（tenantId）
        admin = User.builder()
                .id(adminId)
                .name("管理员")
                .userType("super_admin")
                .tenantId(tenantId)
                .authStatus("approved")
                .createdAt(LocalDateTime.now())
                .build();

        user = User.builder()
                .id(userId)
                .name("测试用户")
                .userType("业主")
                .tenantId(tenantId)
                .authStatus("approved")
                .createdAt(LocalDateTime.now())
                .build();

        idleItem = IdleItem.builder()
                .id(itemId)
                .userId(userId)
                .tenantId(tenantId)
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
                .tenantId(tenantId)
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
        // 准备
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(idleItemRepository.findAll()).thenReturn(List.of(idleItem));
        when(helpRequestRepository.findAll()).thenReturn(List.of(helpRequest));
        when(borrowRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(userRepository.findAll()).thenReturn(List.of(user));

        // 执行
        DashboardDTO result = adminService.getDashboard(adminId);

        // 断言
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
        // 准备
        Page<User> userPage = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        when(userRepository.findByAuthStatus(eq("pending"), any(PageRequest.class)))
                .thenReturn(userPage);

        // 执行
        PageDTO<UserDTO> result = adminService.getAudits("pending", 0, 10);

        // 断言
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("获取审核列表 - 空状态时排除 registering 用户")
    void should_returnAllNonRegistering_when_statusEmpty() {
        // 准备
        Page<User> userPage = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        when(userRepository.findByAuthStatusNot(eq(BizStatus.REGISTERING), any(PageRequest.class)))
                .thenReturn(userPage);

        // 执行
        PageDTO<UserDTO> result = adminService.getAudits(null, 0, 10);

        // 断言
        assertThat(result.getContent()).hasSize(1);
    }

    // ==================== getAuditCounts ====================

    @Test
    @DisplayName("获取审核计数 - 返回各状态的数量")
    void should_returnAuditCounts_when_called() {
        // 准备（"全部住户"页签排除管理员账号，因此 approved 使用带 userType 排除的计数）
        when(userRepository.countByAuthStatus("pending")).thenReturn(5L);
        when(userRepository.countByAuthStatusAndUserTypeNotIn(eq("approved"), anyList())).thenReturn(10L);
        when(userRepository.countByAuthStatus("rejected")).thenReturn(2L);
        when(userRepository.countByAuthStatusNot(BizStatus.REGISTERING)).thenReturn(17L);

        // 执行
        Map<String, Long> result = adminService.getAuditCounts();

        // 断言
        assertThat(result).containsEntry("pending", 5L);
        assertThat(result).containsEntry("approved", 10L);
        assertThat(result).containsEntry("rejected", 2L);
        assertThat(result).containsEntry("all", 17L);
    }

    // ==================== auditUser ====================

    @Test
    @DisplayName("审核用户 - 通过审核")
    void should_approveUser_when_approved() {
        // 准备
        AuditRequest req = new AuditRequest();
        req.setApproved(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        Map<String, Object> result = adminService.auditUser(adminId, userId, req);

        // 断言
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("审核通过");
        assertThat(user.getAuthStatus()).isEqualTo("approved");
        assertThat(user.getRejectReason()).isNull();
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("审核用户 - 拒绝审核并记录原因")
    void should_rejectUser_when_rejected() {
        // 准备
        AuditRequest req = new AuditRequest();
        req.setApproved(false);
        req.setReason("资料不完整");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        Map<String, Object> result = adminService.auditUser(adminId, userId, req);

        // 断言
        assertThat(result.get("message")).isEqualTo("已拒绝");
        assertThat(user.getAuthStatus()).isEqualTo("rejected");
        assertThat(user.getRejectReason()).isEqualTo("资料不完整");
        // 拒绝即踢：tokenVersion +1 并断开其在线 WS 连接
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(chatWebSocketHandler).closeUserSessions(userId.toString());
    }

    @Test
    @DisplayName("审核用户 - 用户不存在时抛出异常")
    void should_throwException_when_userNotFound() {
        // 准备
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // 执行 & 断言
        assertThatThrownBy(() -> adminService.auditUser(adminId, userId, new AuditRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("用户不存在");
    }

    // ==================== getContentCounts ====================

    @Test
    @DisplayName("获取内容计数 - 返回各状态的数量")
    void should_returnContentCounts() {
        // 准备
        when(idleItemRepository.countByStatus("online")).thenReturn(10L);
        when(helpRequestRepository.countByStatus("online")).thenReturn(5L);
        when(idleItemRepository.countByStatus("borrowing")).thenReturn(3L);
        when(helpRequestRepository.countByStatus("helping")).thenReturn(2L);
        when(idleItemRepository.countByStatus("completed")).thenReturn(8L);
        when(helpRequestRepository.countByStatus("completed")).thenReturn(4L);
        when(idleItemRepository.countByStatus(BizStatus.DELETED)).thenReturn(1L);
        when(helpRequestRepository.countByStatus(BizStatus.DELETED)).thenReturn(1L);

        // 执行
        Map<String, Long> result = adminService.getContentCounts();

        // 断言
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
        // 准备
        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(itemId, "idle");

        // 断言
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("idle");
        assertThat(result.getTitle()).isEqualTo("闲置物品");
    }

    @Test
    @DisplayName("获取内容详情 - help类型返回详情")
    void should_returnHelpDetail_when_typeHelp() {
        // 准备
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(helpId, "help");

        // 断言
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("help");
        assertThat(result.getTitle()).isEqualTo("求助信息");
    }

    @Test
    @DisplayName("获取内容详情 - idle借用中状态包含对方信息和时间范围")
    void should_returnBorrowingPeerInfo_when_idleStatusBorrowing() {
        // 准备：借用中的闲置物品
        IdleItem borrowingItem = IdleItem.builder()
                .id(itemId)
                .userId(userId)
                .tenantId(tenantId)
                .title("借用中的物品")
                .postType("LEND")
                .status("borrowing")
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();

        User borrower = User.builder()
                .id(3L)
                .name("借入人")
                .userType("业主")
                .tenantId(tenantId)
                .build();

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(300L)
                .idleId(itemId)
                .borrowerId(3L)
                .status("active")
                .startDate(LocalDate.of(2026, 7, 20))
                .durationDays(7)
                .createdAt(LocalDateTime.now())
                .build();

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(borrowingItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(borrowRequestRepository.findByIdleId(itemId)).thenReturn(List.of(borrowRequest));
        when(userRepository.findById(3L)).thenReturn(Optional.of(borrower));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(itemId, "idle");

        // 断言：对方信息和时间范围已填充
        assertThat(result).isNotNull();
        assertThat(result.getPeerName()).isEqualTo("借入人");
        assertThat(result.getTimeStart()).isEqualTo(LocalDate.of(2026, 7, 20).atStartOfDay());
        assertThat(result.getTimeEnd()).isEqualTo(LocalDate.of(2026, 7, 27).atStartOfDay());
        // 已完成特有字段应为空
        assertThat(result.getPeerRating()).isNull();
        assertThat(result.getPublisherRatingStars()).isNull();
        assertThat(result.getApplyAt()).isNull();
    }

    @Test
    @DisplayName("获取内容详情 - idle已完成状态包含评价和时间线")
    void should_returnCompletedPeerInfo_when_idleStatusCompleted() {
        // 准备：已完成的闲置物品
        IdleItem completedItem = IdleItem.builder()
                .id(itemId)
                .userId(userId)
                .tenantId(tenantId)
                .title("已完成的物品")
                .postType("LEND")
                .status("completed")
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();

        User borrower = User.builder()
                .id(3L)
                .name("借入人")
                .userType("业主")
                .tenantId(tenantId)
                .build();

        LocalDateTime now = LocalDateTime.now();
        BorrowRequest completedBorrow = BorrowRequest.builder()
                .id(300L)
                .idleId(itemId)
                .borrowerId(3L)
                .status("returned")
                .startDate(LocalDate.of(2026, 7, 15))
                .durationDays(5)
                .createdAt(now.minusDays(10))
                .build();
        completedBorrow.setUpdatedAt(now);

        Rating pubRating = Rating.builder()
                .id(1L)
                .borrowId(300L)
                .fromUserId(3L)
                .score(5)
                .build();

        Rating peerRating = Rating.builder()
                .id(2L)
                .borrowId(300L)
                .fromUserId(userId)
                .score(4)
                .build();

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(completedItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(borrowRequestRepository.findByIdleId(itemId)).thenReturn(List.of(completedBorrow));
        when(userRepository.findById(3L)).thenReturn(Optional.of(borrower));
        when(ratingRepository.getAverageScore(3L)).thenReturn(4.5);
        when(ratingRepository.findByBorrowIdAndFromUserId(300L, 3L)).thenReturn(Optional.of(pubRating));
        when(ratingRepository.findByBorrowIdAndFromUserId(300L, userId)).thenReturn(Optional.of(peerRating));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(itemId, "idle");

        // 断言：对方信息
        assertThat(result).isNotNull();
        assertThat(result.getPeerName()).isEqualTo("借入人");
        assertThat(result.getPeerRating()).isEqualTo(4.5);
        // 时间范围
        assertThat(result.getTimeStart()).isEqualTo(LocalDate.of(2026, 7, 15).atStartOfDay());
        assertThat(result.getTimeEnd()).isEqualTo(LocalDate.of(2026, 7, 20).atStartOfDay());
        // 评价
        assertThat(result.getPublisherRatingScore()).isEqualTo(5.0);
        assertThat(result.getPeerRatingScore()).isEqualTo(4.0);
        // 时间线
        assertThat(result.getApplyAt()).isEqualTo(completedBorrow.getCreatedAt());
        assertThat(result.getApproveAt()).isEqualTo(LocalDate.of(2026, 7, 15).atStartOfDay());
        assertThat(result.getCompleteAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("获取内容详情 - help帮助中状态包含对方信息")
    void should_returnHelpingPeerInfo_when_helpStatusHelping() {
        // 准备
        HelpRequest helpingItem = HelpRequest.builder()
                .id(helpId)
                .userId(userId)
                .tenantId(tenantId)
                .title("帮助中的求助")
                .status("helping")
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();

        User helper = User.builder()
                .id(4L)
                .name("帮助者")
                .userType("业主")
                .tenantId(tenantId)
                .build();

        HelpApplication activeApp = HelpApplication.builder()
                .id(500L)
                .helpId(helpId)
                .helperId(4L)
                .status(BizStatus.APPROVED)
                .createdAt(LocalDateTime.now())
                .build();

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpingItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(helpApplicationRepository.findByHelpId(helpId)).thenReturn(List.of(activeApp));
        when(userRepository.findById(4L)).thenReturn(Optional.of(helper));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(helpId, "help");

        // 断言
        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("help");
        assertThat(result.getPeerName()).isEqualTo("帮助者");
        // 已完成特有字段应为空
        assertThat(result.getPeerRating()).isNull();
        assertThat(result.getPublisherRatingStars()).isNull();
        assertThat(result.getApplyAt()).isNull();
    }

    @Test
    @DisplayName("获取内容详情 - help已完成状态包含评价和时间线")
    void should_returnHelpCompletedPeerInfo_when_helpStatusCompleted() {
        // 准备
        HelpRequest completedItem = HelpRequest.builder()
                .id(helpId)
                .userId(userId)
                .tenantId(tenantId)
                .title("已完成的求助")
                .status("completed")
                .timeStart(LocalDateTime.of(2026, 7, 10, 9, 0))
                .isProxy(false)
                .createdAt(LocalDateTime.now())
                .build();

        User helper = User.builder()
                .id(4L)
                .name("帮助者")
                .userType("业主")
                .tenantId(tenantId)
                .build();

        LocalDateTime now = LocalDateTime.now();
        HelpApplication completedApp = HelpApplication.builder()
                .id(500L)
                .helpId(helpId)
                .helperId(4L)
                .status("completed")
                .createdAt(now.minusDays(5))
                .build();
        completedApp.setCompletedAt(now);

        Rating pubRating = Rating.builder()
                .id(1L)
                .helpApplicationId(500L)
                .fromUserId(4L)
                .score(5)
                .build();

        Rating peerR = Rating.builder()
                .id(2L)
                .helpApplicationId(500L)
                .fromUserId(userId)
                .score(3)
                .build();

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(completedItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(helpApplicationRepository.findByHelpId(helpId)).thenReturn(List.of(completedApp));
        when(userRepository.findById(4L)).thenReturn(Optional.of(helper));
        when(ratingRepository.getAverageScore(4L)).thenReturn(4.0);
        when(ratingRepository.findByHelpApplicationIdAndFromUserId(500L, 4L)).thenReturn(Optional.of(pubRating));
        when(ratingRepository.findByHelpApplicationIdAndFromUserId(500L, userId)).thenReturn(Optional.of(peerR));

        // 执行
        ContentItemDTO result = adminService.getContentDetail(helpId, "help");

        // 断言
        assertThat(result).isNotNull();
        assertThat(result.getPeerName()).isEqualTo("帮助者");
        assertThat(result.getPeerRating()).isEqualTo(4.0);
        assertThat(result.getPublisherRatingScore()).isEqualTo(5.0);
        assertThat(result.getPeerRatingScore()).isEqualTo(3.0);
        // 时间线：approveAt 用 item.getTimeStart()
        assertThat(result.getApplyAt()).isEqualTo(completedApp.getCreatedAt());
        assertThat(result.getApproveAt()).isEqualTo(LocalDateTime.of(2026, 7, 10, 9, 0));
        assertThat(result.getCompleteAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("获取内容详情 - 不支持的类型抛出异常")
    void should_throwException_when_unsupportedType() {
        // 执行 & 断言
        assertThatThrownBy(() -> adminService.getContentDetail(itemId, "unknown"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的类型，请使用 idle 或 help");
    }

    // ==================== removeContent ====================

    @Test
    @DisplayName("删除内容 - 删除idle类型内容")
    void should_removeIdleContent_when_validRequest() {
        // 准备
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("idle");
        req.setReasons(List.of("违规内容"));
        req.setCustomReason("包含广告信息");

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(idleItemRepository.save(any(IdleItem.class))).thenReturn(idleItem);
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        Map<String, Object> result = adminService.removeContent(adminId, itemId, req);

        // 断言
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("message")).isEqualTo("内容已删除");
        assertThat(idleItem.getStatus()).isEqualTo(BizStatus.DELETED);
        assertThat(idleItem.getViolationType()).isEqualTo("违规内容");
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("删除内容 - 删除help类型内容")
    void should_removeHelpContent_when_validRequest() {
        // 准备
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("help");
        req.setReasons(List.of("虚假信息"));

        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenReturn(helpRequest);
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        Map<String, Object> result = adminService.removeContent(adminId, helpId, req);

        // 断言
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(helpRequest.getStatus()).isEqualTo(BizStatus.DELETED);
    }

    @Test
    @DisplayName("删除内容 - reasons为null时默认使用'违规'")
    void should_useDefaultViolationType_when_reasonsNull() {
        // 准备
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("idle");
        req.setCustomReason("自定义原因");

        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(idleItemRepository.save(any(IdleItem.class))).thenReturn(idleItem);
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        adminService.removeContent(adminId, itemId, req);

        // 断言
        assertThat(idleItem.getViolationType()).isEqualTo("违规");
    }

    @Test
    @DisplayName("删除内容 - 不支持的目标类型抛出异常")
    void should_throwException_when_unsupportedTargetType() {
        // 准备
        ContentOfflineRequest req = new ContentOfflineRequest();
        req.setTargetType("unknown");

        // 执行 & 断言
        assertThatThrownBy(() -> adminService.removeContent(adminId, itemId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的目标类型");
    }

    // ==================== proxyPublishIdle ====================

    @Test
    @DisplayName("代发闲置 - 正常代发闲置物品")
    void should_proxyPublishIdle_when_validInput() {
        // 准备
        IdleItemRequest req = new IdleItemRequest();
        req.setUserId(userId);
        req.setTitle("代发物品");
        req.setDescription("测试代发");
        req.setPostType("LEND");
        req.setCategory("数码");
        req.setPrice(java.math.BigDecimal.TEN);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(idleItemRepository.save(any(IdleItem.class))).thenAnswer(inv -> {
            IdleItem item = inv.getArgument(0);
            item.setId(itemId);
            return item;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        IdleItemDTO result = adminService.proxyPublishIdle(adminId, req);

        // 断言
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("代发物品");
        assertThat(result.getStatus()).isEqualTo("online");
        verify(operationLogRepository).save(any(OperationLog.class));
    }

    @Test
    @DisplayName("代发闲置 - userId为null时使用adminId")
    void should_useAdminId_when_userIdNull() {
        // 准备
        IdleItemRequest req = new IdleItemRequest();
        req.setTitle("管理员代发");
        req.setDescription("测试");
        req.setPostType("LEND");
        req.setCategory("数码");

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(idleItemRepository.save(any(IdleItem.class))).thenAnswer(inv -> {
            IdleItem item = inv.getArgument(0);
            item.setId(itemId);
            return item;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        IdleItemDTO result = adminService.proxyPublishIdle(adminId, req);

        // 断言
        assertThat(result.getUserId()).isEqualTo(adminId);
    }

    // ==================== proxyPublishHelp ====================

    @Test
    @DisplayName("代发求助 - 正常代发求助")
    void should_proxyPublishHelp_when_validInput() {
        // 准备
        HelpRequestDTO req = new HelpRequestDTO();
        req.setUserId(userId);
        req.setTitle("代发求助");
        req.setDescription("测试代发求助");
        req.setCategory("搬家");
        req.setIsUrgent(true);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(helpRequestRepository.save(any(HelpRequest.class))).thenAnswer(inv -> {
            HelpRequest hr = inv.getArgument(0);
            hr.setId(helpId);
            return hr;
        });
        when(operationLogRepository.save(any(OperationLog.class))).thenReturn(new OperationLog());

        // 执行
        HelpResponseDTO result = adminService.proxyPublishHelp(adminId, req);

        // 断言
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
        // 准备
        BorrowRequest br = BorrowRequest.builder()
                .id(300L)
                .idleId(itemId)
                .borrowerId(userId)
                .status("returned")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(borrowRequestRepository.findByStatus("returned")).thenReturn(List.of(br));
        when(idleItemRepository.findById(itemId)).thenReturn(Optional.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 执行
        PageDTO<Map<String, Object>> result = adminService.getRecords(adminId, "borrow", 0, 10);

        // 断言
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).get("type")).isEqualTo("borrow");
    }

    @Test
    @DisplayName("获取记录 - 不支持的类型抛出异常")
    void should_throwException_when_unsupportedRecordType() {
        // 准备（先解析管理员所属小区，再校验类型）
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // 执行 & 断言
        assertThatThrownBy(() -> adminService.getRecords(adminId, "unknown", 0, 10))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("不支持的类型，请使用 borrow 或 help");
    }

    // ==================== getOperationLogs ====================

    @Test
    @DisplayName("获取操作日志 - 返回分页日志")
    void should_returnOperationLogs_when_logsExist() {
        // 准备
        OperationLog log = OperationLog.builder()
                .id(400L)
                .adminId(adminId)
                .action("approve_user")
                .targetType("user")
                .targetId(userId)
                .detail("审核通过")
                .createdAt(LocalDateTime.now())
                .build();

        // 仅超级管理员可查看，且日志按同小区管理员过滤；service 先全量过滤再内存分页
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, user));
        when(operationLogRepository.findAll(any(Sort.class))).thenReturn(List.of(log));

        // 执行
        PageDTO<OperationLogDTO> result = adminService.getOperationLogs(adminId, 0, 10);

        // 断言
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAction()).isEqualTo("approve_user");
        assertThat(result.getContent().get(0).getAdminName()).isEqualTo("管理员");
        // 分页计数必须是过滤后的实际条数，而非全库计数
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    // ==================== getBuildings ====================

    @Test
    @DisplayName("获取楼栋列表 - 返回管理员所属小区的楼栋")
    void should_returnBuildings_when_called() {
        // 准备
        Building building = Building.builder()
                .id(500L)
                .tenantId(tenantId)
                .name("3栋")
                .build();
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(buildingRepository.findByTenantId(tenantId)).thenReturn(List.of(building));

        // 执行
        List<Map<String, Object>> result = adminService.getBuildings(adminId);

        // 断言
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("3栋");
    }

    // ==================== exportData ====================

    @Test
    @DisplayName("导出数据 - idle类型导出闲置物品数据")
    void should_exportIdleData_when_typeIdle() {
        // 准备
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(idleItemRepository.findAll()).thenReturn(List.of(idleItem));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 执行
        List<Map<String, Object>> result = adminService.exportData(adminId, "idle");

        // 断言
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("type")).isEqualTo("idle");
        assertThat(result.get(0).get("title")).isEqualTo("闲置物品");
    }

    @Test
    @DisplayName("导出数据 - help类型导出求助数据")
    void should_exportHelpData_when_typeHelp() {
        // 准备
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(helpRequestRepository.findAll()).thenReturn(List.of(helpRequest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // 执行
        List<Map<String, Object>> result = adminService.exportData(adminId, "help");

        // 断言
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("type")).isEqualTo("help");
    }

    @Test
    @DisplayName("导出数据 - 未知类型返回空列表")
    void should_returnEmpty_when_unknownExportType() {
        // 准备（导出前仍会解析管理员所属小区）
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // 执行
        List<Map<String, Object>> result = adminService.exportData(adminId, "unknown");

        // 断言
        assertThat(result).isEmpty();
    }
}
