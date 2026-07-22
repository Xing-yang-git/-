package com.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.BizStatus;
import com.platform.common.PostType;
import com.platform.common.UserFormatter;
import com.platform.model.dto.AuditRequest;
import com.platform.model.dto.ContentItemDTO;
import com.platform.model.dto.ContentOfflineRequest;
import com.platform.model.dto.DashboardDTO;
import com.platform.model.dto.HelpRequestDTO;
import com.platform.model.dto.HelpResponseDTO;
import com.platform.model.dto.IdleItemDTO;
import com.platform.model.dto.IdleItemRequest;
import com.platform.model.dto.OperationLogDTO;
import com.platform.model.dto.PageDTO;
import com.platform.model.dto.ResidentDTO;
import com.platform.model.dto.UserDTO;
import com.platform.model.entity.BorrowRequest;
import com.platform.model.entity.Building;
import com.platform.model.entity.HelpApplication;
import com.platform.model.entity.HelpRequest;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.Notification;
import com.platform.model.entity.OperationLog;
import com.platform.model.entity.Rating;
import com.platform.model.entity.Room;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.Unit;
import com.platform.model.entity.User;
import com.platform.repository.BorrowRequestRepository;
import com.platform.repository.BuildingRepository;
import com.platform.repository.HelpApplicationRepository;
import com.platform.repository.HelpRequestRepository;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.OperationLogRepository;
import com.platform.repository.RatingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import com.platform.repository.UserRepository;
import com.platform.websocket.ChatWebSocketHandler;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IdleItemRepository idleItemRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final HelpApplicationRepository helpApplicationRepository;
    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;
    private final NotificationService notificationService;
    private final TenantRepository tenantRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final RatingRepository ratingRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatWebSocketHandler chatWebSocketHandler;

    public AdminService(IdleItemRepository idleItemRepository,
                        HelpRequestRepository helpRequestRepository,
                        BorrowRequestRepository borrowRequestRepository,
                        HelpApplicationRepository helpApplicationRepository,
                        UserRepository userRepository,
                        OperationLogRepository operationLogRepository,
                        NotificationService notificationService,
                        TenantRepository tenantRepository,
                        BuildingRepository buildingRepository,
                        UnitRepository unitRepository,
                        RoomRepository roomRepository,
                        RatingRepository ratingRepository,
                        PasswordEncoder passwordEncoder,
                        ChatWebSocketHandler chatWebSocketHandler) {
        this.idleItemRepository = idleItemRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.helpApplicationRepository = helpApplicationRepository;
        this.userRepository = userRepository;
        this.operationLogRepository = operationLogRepository;
        this.notificationService = notificationService;
        this.tenantRepository = tenantRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.roomRepository = roomRepository;
        this.ratingRepository = ratingRepository;
        this.passwordEncoder = passwordEncoder;
        this.chatWebSocketHandler = chatWebSocketHandler;
    }

    // ==================== 仪表盘 ====================

    /**
     * 获取管理员所属小区的仪表盘统计数据，包括在线数量、本月发布、完成率、活跃用户等。
     */
    public DashboardDTO getDashboard(Long adminId) {
        Long tenantId = getAdminTenantId(adminId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        // 加载闲置物品数据
        List<IdleItem> allIdle = idleItemRepository.findAll().stream()
                .filter(i -> tenantId.equals(i.getTenantId()))
                .collect(Collectors.toList());
        long onlineLendCount = allIdle.stream()
                .filter(i -> BizStatus.ONLINE.equals(i.getStatus()) && PostType.LEND.equals(i.getPostType()))
                .count();
        long onlineWantedCount = allIdle.stream()
                .filter(i -> BizStatus.ONLINE.equals(i.getStatus()) && PostType.WANTED.equals(i.getPostType()))
                .count();

        // 加载求助数据
        List<HelpRequest> allHelp = helpRequestRepository.findAll().stream()
                .filter(h -> tenantId.equals(h.getTenantId()))
                .collect(Collectors.toList());
        long onlineHelpCount = allHelp.stream()
                .filter(h -> BizStatus.ONLINE.equals(h.getStatus()))
                .count();

        // 加载借用数据（按租户过滤）
        List<BorrowRequest> allBorrows = loadBorrowRequestsForTenant(tenantId);

        // 本月统计
        long monthlyIdlePublishes = allIdle.stream()
                .filter(i -> isWithinMonth(i.getCreatedAt(), monthStart, monthEnd))
                .count();
        long monthlyHelpPublishes = allHelp.stream()
                .filter(h -> isWithinMonth(h.getCreatedAt(), monthStart, monthEnd))
                .count();
        long monthlyCompletedBorrows = allBorrows.stream()
                .filter(b -> BizStatus.RETURNED.equals(b.getStatus())
                        && isWithinMonth(b.getCreatedAt(), monthStart, monthEnd))
                .count();
        long monthlyTotalBorrows = allBorrows.stream()
                .filter(b -> isWithinMonth(b.getCreatedAt(), monthStart, monthEnd))
                .count();
        double completionRate = monthlyTotalBorrows > 0
                ? (double) monthlyCompletedBorrows / monthlyTotalBorrows : 0.0;

        long monthlyActiveUsers = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .filter(u -> isWithinMonth(u.getCreatedAt(), monthStart, monthEnd))
                .count();

        long damageCount = allBorrows.stream()
                .filter(b -> b.getDamageType() != null && !"none".equals(b.getDamageType()))
                .count();

        // 构建统计数据
        List<DashboardDTO.CategoryStat> categoryStats = buildCategoryStats(allIdle);
        long monthlyPublishes = monthlyIdlePublishes + monthlyHelpPublishes;
        List<DashboardDTO.ItemStat> itemStats = buildItemStats(
                onlineLendCount, onlineWantedCount, onlineHelpCount,
                monthlyPublishes, monthlyCompletedBorrows,
                monthlyActiveUsers, damageCount);

        return DashboardDTO.builder()
                .onlineIdleCount(onlineLendCount + onlineWantedCount)
                .onlineHelpCount(onlineHelpCount)
                .monthlyPublishes(monthlyPublishes)
                .monthlyCompletedBorrows(monthlyCompletedBorrows)
                .completionRate(Math.round(completionRate * 1000.0) / 1000.0)
                .monthlyActiveUsers(monthlyActiveUsers)
                .damageCount(damageCount)
                .categoryStats(categoryStats)
                .itemStats(itemStats)
                .build();
    }

    /**
     * 加载该租户下的所有借用请求（通过关联的闲置物品过滤）。
     */
    private List<BorrowRequest> loadBorrowRequestsForTenant(Long tenantId) {
        return borrowRequestRepository.findAll().stream()
                .filter(b -> {
                    IdleItem idle = idleItemRepository.findById(b.getIdleId()).orElse(null);
                    return idle != null && tenantId.equals(idle.getTenantId());
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断日期是否在 [start, end] 闭区间内（含 null 安全）。
     */
    private boolean isWithinMonth(LocalDateTime dt, LocalDateTime start, LocalDateTime end) {
        return dt != null && !dt.isBefore(start) && !dt.isAfter(end);
    }

    /**
     * 根据在线闲置物品构建分类统计。
     */
    private List<DashboardDTO.CategoryStat> buildCategoryStats(List<IdleItem> allIdle) {
        Map<String, Long> categoryCountMap = allIdle.stream()
                .filter(i -> BizStatus.ONLINE.equals(i.getStatus()))
                .collect(Collectors.groupingBy(
                        i -> i.getCategory() != null ? i.getCategory() : "其他",
                        Collectors.counting()));
        return categoryCountMap.entrySet().stream()
                .map(e -> DashboardDTO.CategoryStat.builder()
                        .category(e.getKey())
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 构建仪表盘指标列表。
     */
    private List<DashboardDTO.ItemStat> buildItemStats(long onlineLend, long onlineWanted,
            long onlineHelp, long monthlyPublishes, long monthlyCompletedBorrows,
            long monthlyActiveUsers, long damageCount) {
        List<DashboardDTO.ItemStat> itemStats = new ArrayList<>();
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线闲置(LEND)").value(onlineLend).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线闲置(WANTED)").value(onlineWanted).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线求助").value(onlineHelp).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("本月发布").value(monthlyPublishes).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("本月完成借入").value(monthlyCompletedBorrows).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("月活用户").value(monthlyActiveUsers).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("损坏物品").value(damageCount).build());
        return itemStats;
    }

    // ==================== 审核管理 ====================

    /**
     * 按认证状态筛选获取审核列表。
     * @param status  "pending" / "approved" / "rejected" / null（全部非 registering 状态）
     * @param page    页码（从 0 开始）
     * @param size    每页条数
     */
    private static final List<String> ADMIN_USER_TYPES = Arrays.asList("admin", "super_admin");

    public PageDTO<UserDTO> getAudits(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;

        if (status != null && !status.isEmpty()) {
            if (BizStatus.APPROVED.equals(status)) {
                // "全部住户"页签：仅显示真实住户，排除管理员账号
                userPage = userRepository.findByAuthStatusAndUserTypeNotIn(BizStatus.APPROVED, ADMIN_USER_TYPES, pageRequest);
            } else {
                userPage = userRepository.findByAuthStatus(status, pageRequest);
            }
        } else {
            // "all"页签：排除仍处于 "registering" 状态（尚未完成注册）的用户
            userPage = userRepository.findByAuthStatusNot("registering", pageRequest);
        }

        List<UserDTO> dtos = userPage.getContent().stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());

        return PageDTO.<UserDTO>builder()
                .content(dtos)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    /**
     * 获取各审核页签的数量统计。
     */
    public Map<String, Long> getAuditCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put(BizStatus.PENDING, userRepository.countByAuthStatus(BizStatus.PENDING));
        counts.put(BizStatus.APPROVED, userRepository.countByAuthStatusAndUserTypeNotIn(BizStatus.APPROVED, ADMIN_USER_TYPES));
        counts.put(BizStatus.REJECTED, userRepository.countByAuthStatus(BizStatus.REJECTED));
        counts.put("all", userRepository.countByAuthStatusNot("registering"));
        return counts;
    }

    public Map<String, Object> auditUser(Long adminId, Long userId, AuditRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setAuthStatus(req.getApproved() ? BizStatus.APPROVED : BizStatus.REJECTED);
        if (req.getApproved()) {
            user.setRejectReason(null);
        } else {
            user.setRejectReason(req.getReason());
            // 拒绝即踢：版本+1 使其现有 token 立即失效，并断开其在线 WS 连接（通过分支不打断已登录用户）
            user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        }
        userRepository.save(user);
        if (!req.getApproved()) {
            chatWebSocketHandler.closeUserSessions(user.getId().toString());
        }

        String title = req.getApproved() ? "实名认证已通过" : "实名认证被拒绝";
        String content = req.getApproved()
                ? "您的实名认证申请已通过审核"
                : "您的实名认证申请被拒绝"
                        + (req.getReason() != null ? "，原因：" + req.getReason() : "");
        createNotification(userId, "audit_result", title, content, null);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setTenantId(getAdminTenantId(adminId));
        log.setAction(req.getApproved() ? "approve_user" : "reject_user");
        log.setTargetType("user");
        log.setTargetId(userId);
        log.setDetail(req.getReason());
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", req.getApproved() ? "审核通过" : "已拒绝");
        return result;
    }

    // ==================== 内容管理 ====================

    /**
     * 按状态页签、类型、楼栋和搜索关键词筛选，获取分页内容列表。
     *
     * @param statusTab "showing"|"progressing"|"completed"|"violation"|"all"
     * @param type      "idle"|"help"|null（两者都查）
     * @param building  如 "3栋" | null
     * @param search    关键词 | null
     * @param page      页码（从 0 开始）
     * @param size      每页条数
     * @return 分页内容列表
     */
    /**
     * 获取内容列表，支持按状态页签、类型、楼栋、单元、关键词筛选，按 createdAt 降序分页。
     */
    public PageDTO<ContentItemDTO> getContentList(Long adminId, String statusTab, String type, String building,
                                                   String unit, String search, int page, int size) {
        Long tenantId = getAdminTenantId(adminId);

        // 解析楼栋筛选条件（无匹配用户时直接返回空页）
        List<Long> buildingUserIds = resolveBuildingFilter(building, unit);
        if (buildingUserIds != null && buildingUserIds.isEmpty()) {
            return buildEmptyPage(page, size);
        }

        // 将状态页签映射为数据库状态值列表
        String effectiveTab = (statusTab != null && !statusTab.isEmpty()) ? statusTab : "all";
        List<String> idleStatuses = mapStatusTabToIdleStatuses(effectiveTab);
        List<String> helpStatuses = mapStatusTabToHelpStatuses(effectiveTab);

        // 查询并转换为 DTO
        List<ContentItemDTO> allItems = new ArrayList<>();
        if (type == null || "idle".equals(type)) {
            fetchIdleItems(idleStatuses, buildingUserIds, tenantId, search)
                    .forEach(item -> allItems.add(toContentItemDTO(item)));
        }
        if (type == null || "help".equals(type)) {
            fetchHelpItems(helpStatuses, buildingUserIds, tenantId, search)
                    .forEach(item -> allItems.add(toContentItemDTO(item)));
        }

        // 排序并分页
        sortContentByCreatedAtDesc(allItems);
        return paginateInMemory(allItems, page, size);
    }

    /**
     * 解析楼栋筛选条件：无筛选条件返回 null，有筛选但无匹配用户返回空列表。
     *
     * @return null = 不需要筛选；空列表 = 筛选条件无匹配（应返回空结果）；非空列表 = 匹配的用户 ID
     */
    private List<Long> resolveBuildingFilter(String building, String unit) {
        if (building == null || building.isEmpty()) {
            return null;
        }
        return resolveBuildingUserIds(building, unit);
    }

    /**
     * 构建空分页结果。
     */
    private <T> PageDTO<T> buildEmptyPage(int page, int size) {
        return PageDTO.<T>builder()
                .content(new ArrayList<>())
                .totalElements(0)
                .totalPages(0)
                .currentPage(page)
                .size(size)
                .build();
    }

    /**
     * 按 createdAt 降序排列 ContentItemDTO 列表（null 值排最后）。
     */
    private void sortContentByCreatedAtDesc(List<ContentItemDTO> items) {
        items.sort((a, b) -> {
            LocalDateTime ta = a.getCreatedAt();
            LocalDateTime tb = b.getCreatedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
    }

    /**
     * 获取内容详情，包含对方信息、评价和违规信息。
     *
     * @param id   内容 ID
     * @param type "idle" 或 "help"
     * @return 内容详情 DTO
     */
    public ContentItemDTO getContentDetail(Long id, String type) {
        if ("idle".equals(type)) {
            IdleItem item = idleItemRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("物品不存在"));
            return toContentItemDTO(item);
        } else if ("help".equals(type)) {
            HelpRequest item = helpRequestRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("求助不存在"));
            return toContentItemDTO(item);
        } else {
            throw new RuntimeException("不支持的类型，请使用 idle 或 help");
        }
    }

    /**
     * 获取各内容状态页签的数量统计。
     *
     * @return 页签名到数量的映射
     */
    public Map<String, Long> getContentCounts() {
        Map<String, Long> counts = new HashMap<>();

        long idleShowing = idleItemRepository.countByStatus(BizStatus.ONLINE);
        long helpShowing = helpRequestRepository.countByStatus(BizStatus.ONLINE);
        counts.put("showing", idleShowing + helpShowing);

        long idleProgressing = idleItemRepository.countByStatus("borrowing");
        long helpProgressing = helpRequestRepository.countByStatus("helping");
        counts.put("progressing", idleProgressing + helpProgressing);

        long idleCompleted = idleItemRepository.countByStatus(BizStatus.COMPLETED);
        long helpCompleted = helpRequestRepository.countByStatus(BizStatus.COMPLETED);
        counts.put("completed", idleCompleted + helpCompleted);

        long idleViolation = idleItemRepository.countByStatus("deleted");
        long helpViolation = helpRequestRepository.countByStatus("deleted");
        counts.put("violation", idleViolation + helpViolation);

        long idleAll = idleShowing + idleProgressing + idleCompleted + idleViolation;
        long helpAll = helpShowing + helpProgressing + helpCompleted + helpViolation;
        counts.put("all", idleAll + helpAll);

        return counts;
    }

    /**
     * 下架（删除）内容并记录违规信息。
     *
     * @param adminId   执行操作的管理员
     * @param contentId 要删除的内容 ID
     * @param req       下架请求，包含类型、原因列表和自定义原因
     * @return 结果 map
     */
    /**
     * 下架（删除）内容并记录违规信息。
     */
    public Map<String, Object> removeContent(Long adminId, Long contentId, ContentOfflineRequest req) {
        String violationType = buildViolationType(req);
        String violationReason = buildViolationReason(req, violationType);

        switch (req.getTargetType()) {
            case "idle" -> removeIdleContent(contentId, adminId, violationType, violationReason);
            case "help" -> removeHelpContent(contentId, adminId, violationType, violationReason);
            default -> throw new RuntimeException("不支持的目标类型");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "内容已删除");
        return result;
    }

    /**
     * 从下架请求中提取违规类型字符串。
     */
    private String buildViolationType(ContentOfflineRequest req) {
        return req.getReasons() != null && !req.getReasons().isEmpty()
                ? String.join("，", req.getReasons())
                : "违规";
    }

    /**
     * 从下架请求中提取违规原因字符串。
     */
    private String buildViolationReason(ContentOfflineRequest req, String fallback) {
        return req.getCustomReason() != null && !req.getCustomReason().isEmpty()
                ? req.getCustomReason()
                : fallback;
    }

    /**
     * 下架闲置物品：标记违规状态、发送通知、记录操作日志。
     */
    private void removeIdleContent(Long contentId, Long adminId, String violationType, String violationReason) {
        IdleItem item = idleItemRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));
        item.setStatus("deleted");
        item.setDelistReason("violation");
        item.setViolationType(violationType);
        item.setViolationReason(violationReason);
        item.setViolatedBy(adminId);
        item.setViolatedAt(LocalDateTime.now());
        idleItemRepository.save(item);

        createNotification(item.getUserId(), "violation",
                "物品被管理员删除", "您的物品「" + item.getTitle() + "」因违规被删除",
                item.getId());

        saveOperationLog(adminId, item.getTenantId(), "remove_content", "idle", contentId, violationReason);
    }

    /**
     * 下架求助信息：标记违规状态、发送通知、记录操作日志。
     */
    private void removeHelpContent(Long contentId, Long adminId, String violationType, String violationReason) {
        HelpRequest item = helpRequestRepository.findById(contentId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));
        item.setStatus("deleted");
        item.setDelistReason("violation");
        item.setViolationType(violationType);
        item.setViolationReason(violationReason);
        item.setViolatedBy(adminId);
        item.setViolatedAt(LocalDateTime.now());
        helpRequestRepository.save(item);

        createNotification(item.getUserId(), "violation",
                "求助被管理员删除", "您的求助「" + item.getTitle() + "」因违规被删除",
                item.getId());

        saveOperationLog(adminId, item.getTenantId(), "remove_content", "help", contentId, violationReason);
    }

    /**
     * 保存操作日志（抽取 removeContent 中重复的日志构建逻辑）。
     */
    private void saveOperationLog(Long adminId, Long tenantId, String action,
                                  String targetType, Long targetId, String detail) {
        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setTenantId(tenantId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);
    }

    // ==================== 代发布 ====================

    public IdleItemDTO proxyPublishIdle(Long adminId, IdleItemRequest req) {
        // tenant_id 为 NOT NULL 列，代发数据必须归属管理员所在租户，否则 save 直接抛约束异常
        Long tenantId = getAdminTenantId(adminId);
        IdleItem item = new IdleItem();
        item.setUserId(req.getUserId() != null ? req.getUserId() : adminId);
        item.setTenantId(tenantId);
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setPostType(req.getPostType());
        item.setCategory(req.getCategory());
        item.setImages(req.getImages());
        item.setPrice(req.getPrice());
        item.setStatus(BizStatus.ONLINE);
        item.setIsProxy(true);
        item.setCreatedAt(LocalDateTime.now());
        item = idleItemRepository.save(item);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setTenantId(tenantId);
        log.setAction("proxy_publish_idle");
        log.setTargetType("idle");
        log.setTargetId(item.getId());
        log.setDetail("管理员代发闲置物品");
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);

        return IdleItemDTO.builder()
                .id(item.getId())
                .userId(item.getUserId())
                .title(item.getTitle())
                .description(item.getDescription())
                .postType(item.getPostType())
                .category(item.getCategory())
                .images(item.getImages())
                .price(item.getPrice())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .build();
    }

    /**
     * 管理员代发求助。
     */
    public HelpResponseDTO proxyPublishHelp(Long adminId, HelpRequestDTO req) {
        Long tenantId = getAdminTenantId(adminId);
        HelpRequest helpRequest = new HelpRequest();
        helpRequest.setUserId(req.getUserId() != null ? req.getUserId() : adminId);
        helpRequest.setTenantId(tenantId);
        helpRequest.setTitle(req.getTitle());
        helpRequest.setDescription(req.getDescription());
        helpRequest.setCategory(req.getCategory());
        helpRequest.setIsUrgent(req.getIsUrgent() != null && req.getIsUrgent());

        // 解析时间字段（格式与 HelpService.publish 一致）
        parseHelpTimeFields(helpRequest, req);

        helpRequest.setStatus(BizStatus.ONLINE);
        helpRequest.setIsProxy(true);
        helpRequest.setCreatedAt(LocalDateTime.now());
        helpRequest = helpRequestRepository.save(helpRequest);

        saveOperationLog(adminId, tenantId, "proxy_publish_help", "help",
                helpRequest.getId(), "管理员代发求助");

        return HelpResponseDTO.builder()
                .id(helpRequest.getId())
                .userId(helpRequest.getUserId())
                .title(helpRequest.getTitle())
                .description(helpRequest.getDescription())
                .category(helpRequest.getCategory())
                .isUrgent(helpRequest.getIsUrgent())
                .status(helpRequest.getStatus())
                .createdAt(helpRequest.getCreatedAt())
                .build();
    }

    /**
     * 解析 HelpRequestDTO 中的 timeStart / timeEnd 字符串并设置到实体上。
     */
    private void parseHelpTimeFields(HelpRequest helpRequest, HelpRequestDTO req) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (req.getTimeStart() != null && !req.getTimeStart().isEmpty()) {
            try {
                helpRequest.setTimeStart(LocalDateTime.parse(req.getTimeStart(), fmt));
            } catch (Exception e) {
                log.debug("Failed to parse timeStart: {}", e.getMessage());
            }
        }
        if (req.getTimeEnd() != null && !req.getTimeEnd().isEmpty()) {
            try {
                helpRequest.setTimeEnd(LocalDateTime.parse(req.getTimeEnd(), fmt));
            } catch (Exception e) {
                log.debug("Failed to parse timeEnd: {}", e.getMessage());
            }
        }
    }

    // ==================== 记录 ====================

    /**
     * 获取交易记录（已归还的借用 / 已完成的帮助），按 createdAt 降序分页。
     */
    public PageDTO<Map<String, Object>> getRecords(Long adminId, String type, int page, int size) {
        Long tenantId = getAdminTenantId(adminId);
        List<Map<String, Object>> allRecords = switch (type) {
            case "borrow" -> loadBorrowRecords(tenantId);
            case "help" -> loadHelpRecords(tenantId);
            default -> throw new RuntimeException("不支持的类型，请使用 borrow 或 help");
        };

        sortByCreatedAtDesc(allRecords);
        return paginateInMemory(allRecords, page, size);
    }

    /**
     * 加载该租户下已归还的借用记录。
     */
    private List<Map<String, Object>> loadBorrowRecords(Long tenantId) {
        List<BorrowRequest> borrows = borrowRequestRepository.findByStatus(BizStatus.RETURNED).stream()
                .filter(br -> {
                    IdleItem idle = idleItemRepository.findById(br.getIdleId()).orElse(null);
                    return idle != null && tenantId.equals(idle.getTenantId());
                })
                .collect(Collectors.toList());
        List<Map<String, Object>> records = new ArrayList<>();
        for (BorrowRequest br : borrows) {
            IdleItem idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
            User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);
            Long ownerId = idleItem != null ? idleItem.getUserId() : null;
            User owner = ownerId != null ? userRepository.findById(ownerId).orElse(null) : null;

            Map<String, Object> map = new HashMap<>();
            map.put("id", br.getId());
            map.put("type", "borrow");
            map.put("title", idleItem != null ? idleItem.getTitle() : "未知物品");
            map.put("borrowerName", borrower != null ? borrower.getName() : "未知用户");
            map.put("ownerName", owner != null ? owner.getName() : "未知用户");
            map.put("status", br.getStatus());
            map.put("damageType", br.getDamageType());
            map.put("createdAt", br.getCreatedAt());
            records.add(map);
        }
        return records;
    }

    /**
     * 加载该租户下已完成的帮助记录。
     */
    private List<Map<String, Object>> loadHelpRecords(Long tenantId) {
        List<HelpApplication> applications = helpApplicationRepository.findByStatus(BizStatus.COMPLETED).stream()
                .filter(app -> {
                    HelpRequest hr = helpRequestRepository.findById(app.getHelpId()).orElse(null);
                    return hr != null && tenantId.equals(hr.getTenantId());
                })
                .collect(Collectors.toList());
        List<Map<String, Object>> records = new ArrayList<>();
        for (HelpApplication app : applications) {
            HelpRequest helpRequest = helpRequestRepository.findById(app.getHelpId()).orElse(null);
            User helper = userRepository.findById(app.getHelperId()).orElse(null);

            Map<String, Object> map = new HashMap<>();
            map.put("id", app.getId());
            map.put("type", "help");
            map.put("title", helpRequest != null ? helpRequest.getTitle() : "未知求助");
            map.put("helperName", helper != null ? helper.getName() : "未知用户");
            map.put("status", app.getStatus());
            map.put("createdAt", app.getCreatedAt());
            records.add(map);
        }
        return records;
    }

    /**
     * 按 createdAt 降序排列记录列表（null 值排最后）。
     */
    private void sortByCreatedAtDesc(List<Map<String, Object>> records) {
        records.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("createdAt");
            LocalDateTime tb = (LocalDateTime) b.get("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });
    }

    /**
     * 对列表进行内存分页并包装为 PageDTO。
     */
    private <T> PageDTO<T> paginateInMemory(List<T> allItems, int page, int size) {
        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<T> pageContent = fromIndex < totalElements
                ? new ArrayList<>(allItems.subList(fromIndex, toIndex))
                : new ArrayList<>();
        return PageDTO.<T>builder()
                .content(pageContent)
                .totalElements((long) totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .build();
    }

    // ==================== 操作日志 ====================

    public PageDTO<OperationLogDTO> getOperationLogs(Long adminId, int page, int size) {
        User admin = findAdmin(adminId);
        requireSuperAdmin(admin);
        Long tenantId = getAdminTenantId(adminId);
        // 收集该租户下所有用户 ID，用于过滤日志
        List<Long> tenantUserIds = userRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .map(User::getId)
                .collect(Collectors.toList());
        // 先按租户过滤全量日志、再内存分页——若先分页后过滤，计数会包含其他租户
        // 导致分页虚高，且整页被过滤掉时数据"跨页丢失"（与 getRecords 分页模式一致）
        List<OperationLog> filteredLogs = operationLogRepository
                .findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(log -> tenantUserIds.contains(log.getAdminId()))
                .collect(Collectors.toList());

        int totalElements = filteredLogs.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<OperationLogDTO> dtos = (fromIndex < totalElements
                ? filteredLogs.subList(fromIndex, toIndex)
                : new ArrayList<OperationLog>()).stream()
                .map(this::toOperationLogDTO)
                .collect(Collectors.toList());

        return PageDTO.<OperationLogDTO>builder()
                .content(dtos)
                .totalElements((long) totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .build();
    }

    // ==================== 数据导出 ====================

    /**
     * 按类型导出数据（idle/help/borrow），仅导出管理员所属租户的数据。
     */
    public List<Map<String, Object>> exportData(Long adminId, String type) {
        Long tenantId = getAdminTenantId(adminId);
        return switch (type) {
            case "idle" -> exportIdleData(tenantId);
            case "help" -> exportHelpData(tenantId);
            case "borrow" -> exportBorrowData(tenantId);
            default -> new ArrayList<>();
        };
    }

    /**
     * 导出闲置物品数据。
     */
    private List<Map<String, Object>> exportIdleData(Long tenantId) {
        List<Map<String, Object>> data = new ArrayList<>();
        List<IdleItem> items = idleItemRepository.findAll().stream()
                .filter(i -> tenantId.equals(i.getTenantId()))
                .collect(Collectors.toList());
        for (IdleItem item : items) {
            User user = userRepository.findById(item.getUserId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId().toString());
            map.put("type", "idle");
            map.put("title", item.getTitle());
            map.put("userName", user != null ? user.getName() : "");
            map.put("postType", item.getPostType());
            map.put("category", item.getCategory());
            map.put("status", item.getStatus());
            map.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
            data.add(map);
        }
        return data;
    }

    /**
     * 导出求助数据。
     */
    private List<Map<String, Object>> exportHelpData(Long tenantId) {
        List<Map<String, Object>> data = new ArrayList<>();
        List<HelpRequest> items = helpRequestRepository.findAll().stream()
                .filter(h -> tenantId.equals(h.getTenantId()))
                .collect(Collectors.toList());
        for (HelpRequest item : items) {
            User user = userRepository.findById(item.getUserId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId().toString());
            map.put("type", "help");
            map.put("title", item.getTitle());
            map.put("userName", user != null ? user.getName() : "");
            map.put("category", item.getCategory());
            map.put("isUrgent", item.getIsUrgent());
            map.put("status", item.getStatus());
            map.put("createdAt", item.getCreatedAt() != null ? item.getCreatedAt().toString() : "");
            data.add(map);
        }
        return data;
    }

    /**
     * 导出借用记录数据。
     */
    private List<Map<String, Object>> exportBorrowData(Long tenantId) {
        List<Map<String, Object>> data = new ArrayList<>();
        List<BorrowRequest> borrows = borrowRequestRepository.findAll().stream()
                .filter(br -> {
                    IdleItem idle = idleItemRepository.findById(br.getIdleId()).orElse(null);
                    return idle != null && tenantId.equals(idle.getTenantId());
                })
                .collect(Collectors.toList());
        for (BorrowRequest br : borrows) {
            IdleItem idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
            Map<String, Object> map = new HashMap<>();
            map.put("id", br.getId().toString());
            map.put("type", "borrow");
            map.put("idleTitle", idleItem != null ? idleItem.getTitle() : "");
            map.put("status", br.getStatus());
            map.put("damageType", br.getDamageType());
            map.put("createdAt", br.getCreatedAt() != null ? br.getCreatedAt().toString() : "");
            data.add(map);
        }
        return data;
    }

    // ==================== 楼栋 ====================

    /**
     * 获取管理员所属小区的全部楼栋名称。
     */
    public List<Map<String, Object>> getBuildings(Long adminId) {
        Long tenantId = getAdminTenantId(adminId);
        List<Building> buildings = buildingRepository.findByTenantId(tenantId);
        return buildings.stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("name", b.getName());
            return map;
        }).collect(Collectors.toList());
    }

    // ==================== 小区数据 ====================

    /**
     * 获取管理员所属小区的嵌套小区数据（小区 + 楼栋 + 单元）。
     * 供前端在登录后缓存使用。
     */
    public Map<String, Object> getCommunityData(Long adminId) {
        Long tenantId = getAdminTenantId(adminId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("小区不存在"));

        List<Building> buildings = buildingRepository.findByTenantId(tenantId);
        // 按楼栋编号排序
        buildings.sort(Comparator.comparingInt(b -> extractNumber(b.getName())));

        List<Map<String, Object>> buildingList = new ArrayList<>();
        for (Building building : buildings) {
            Map<String, Object> bMap = new HashMap<>();
            bMap.put("id", building.getId());
            bMap.put("name", building.getName());

            List<Unit> units = unitRepository.findByBuildingId(building.getId());
            units.sort(Comparator.comparingInt(u -> extractNumber(u.getName())));

            List<Map<String, Object>> unitList = new ArrayList<>();
            for (Unit unit : units) {
                Map<String, Object> uMap = new HashMap<>();
                uMap.put("id", unit.getId());
                uMap.put("name", unit.getName());
                unitList.add(uMap);
            }
            bMap.put("units", unitList);
            buildingList.add(bMap);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tenantName", tenant.getName());
        result.put("buildings", buildingList);
        return result;
    }

    // ==================== 个人资料与密码 ====================

    public Map<String, Object> updateProfile(Long adminId, String name) {
        User admin = findAdmin(adminId);
        if (name != null && !name.trim().isEmpty()) {
            admin.setName(name.trim());
            userRepository.save(admin);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("name", admin.getName());
        result.put("userType", admin.getUserType());
        return result;
    }

    public void updatePassword(Long adminId, String oldPassword, String newPassword) {
        User admin = findAdmin(adminId);
        if (admin.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, admin.getPasswordHash())) {
            throw new RuntimeException("当前密码错误");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(admin);
    }

    // ==================== 管理员账号管理 ====================

    /**
     * 列出同小区下的全部管理员用户。仅 super_admin 可调用。
     */
    public List<Map<String, Object>> getAdmins(Long adminId) {
        User admin = findAdmin(adminId);
        requireSuperAdmin(admin);
        Long tenantId = getAdminTenantId(adminId);

        List<User> admins = userRepository.findByTenantIdAndUserTypeIn(
                tenantId, Arrays.asList("super_admin", "admin"));

        return admins.stream()
                .sorted(Comparator.comparing(User::getCreatedAt))
                .map(u -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", u.getId());
                    map.put("name", u.getName());
                    map.put("userType", u.getUserType());
                    map.put("userTypeLabel", "super_admin".equals(u.getUserType()) ? "超级管理员" : "普通管理员");
                    map.put("phone", maskPhone(u.getPhone()));
                    map.put("createdAt", u.getCreatedAt());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 创建子管理员账号。仅 super_admin 可调用。
     */
    @Transactional
    public Map<String, Object> createAdmin(Long adminId, String name, String phone, String password) {
        User creator = findAdmin(adminId);
        requireSuperAdmin(creator);
        Long tenantId = getAdminTenantId(adminId);

        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("姓名不能为空");
        }
        if (phone == null || phone.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }

        // 校验手机号在小区内唯一
        userRepository.findByPhoneAndTenantId(phone.trim(), tenantId)
                .ifPresent(u -> { throw new RuntimeException("该手机号已存在"); });

        String username = phone.trim();
        User newAdmin = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .name(name.trim())
                .phone(phone.trim())
                .userType("admin")
                .tenantId(tenantId)
                .authStatus(BizStatus.APPROVED)
                .build();
        newAdmin = userRepository.save(newAdmin);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setTenantId(tenantId);
        log.setAction("create_admin");
        log.setTargetType("user");
        log.setTargetId(newAdmin.getId());
        log.setDetail("创建子账号：" + name);
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);

        Map<String, Object> result = new HashMap<>();
        result.put("id", newAdmin.getId());
        result.put("name", newAdmin.getName());
        result.put("userType", newAdmin.getUserType());
        result.put("phone", maskPhone(newAdmin.getPhone()));
        result.put("createdAt", newAdmin.getCreatedAt());
        return result;
    }

    /**
     * 删除子管理员账号。仅 super_admin 可调用，且不能删除自己。
     */
    @Transactional
    public void deleteAdmin(Long adminId, Long targetId) {
        User admin = findAdmin(adminId);
        requireSuperAdmin(admin);

        if (adminId.equals(targetId)) {
            throw new RuntimeException("不能删除自己");
        }

        User target = findAdmin(targetId);
        Long tenantId = getAdminTenantId(adminId);
        if (target.getTenantId() == null || !target.getTenantId().equals(tenantId)) {
            throw new RuntimeException("该管理员不属于当前小区");
        }

        if ("super_admin".equals(target.getUserType())) {
            throw new RuntimeException("不能删除超级管理员");
        }

        userRepository.delete(target);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setTenantId(tenantId);
        log.setAction("delete_admin");
        log.setTargetType("user");
        log.setTargetId(targetId);
        log.setDetail("删除子账号：" + target.getName());
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);
    }

    // ==================== 私有辅助方法：管理员鉴权 ====================

    private User findAdmin(Long adminId) {
        return userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("管理员不存在"));
    }

    private Long getAdminTenantId(Long adminId) {
        User admin = findAdmin(adminId);
        Long tenantId = admin.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("管理员未关联小区");
        }
        return tenantId;
    }

    private void requireSuperAdmin(User admin) {
        if (!"super_admin".equals(admin.getUserType())) {
            throw new RuntimeException("权限不足，仅超级管理员可操作");
        }
    }

    // ==================== 私有辅助方法：格式化 ====================

    /**
     * 按条件搜索住户。
     *
     * @param building 楼栋名称筛选（模糊匹配）
     * @param unit     单元名称筛选（模糊匹配）
     * @param room     房间号筛选（模糊匹配）
     * @param userType "业主" | "租客" | null
     * @param keyword  姓名或手机号关键词
     * @param page     页码（从 0 开始）
     * @param size     每页条数
     * @return 分页住户 DTO 列表
     */
    public PageDTO<ResidentDTO> searchResidents(String building, String unit, String room,
                                                 String userType, String keyword,
                                                 int page, int size) {
        // 将前端英文编码转换为数据库中的中文值
        String dbUserType = switch (userType != null ? userType : "") {
            case "owner"  -> "业主";
            case "tenant" -> "租客";
            default       -> userType;
        };
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findResidents(building, unit, room, dbUserType, keyword, pageRequest);

        List<ResidentDTO> dtos = userPage.getContent().stream()
                .map(u -> ResidentDTO.builder()
                        .id(u.getId())
                        .name(maskName(u.getName()))
                        .room(UserFormatter.formatRoom(u))
                        .userType(UserFormatter.getUserTypeLabel(u.getUserType()))
                        .phone(maskPhone(u.getPhone()))
                        .build())
                .collect(Collectors.toList());

        return PageDTO.<ResidentDTO>builder()
                .content(dtos)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    // ==================== 私有辅助方法：DTO 转换 ====================

    private UserDTO toUserDTO(User user) {
        java.util.List<String> docImages = java.util.Collections.emptyList();
        if (user.getDocImages() != null && !user.getDocImages().isEmpty()) {
            try {
                docImages = OBJECT_MAPPER.readValue(user.getDocImages(), new TypeReference<java.util.List<String>>() {});
            } catch (Exception e) {
                log.debug("解析用户证件图片JSON失败 userId={}", user.getId(), e);
            }
        }

        return UserDTO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .username(user.getUsername())
                .name(user.getName())
                .phone(user.getPhone())
                .userType(user.getUserType())
                .authStatus(user.getAuthStatus())
                .roomId(user.getRoom() != null ? user.getRoom().getId() : null)
                .userRoom(UserFormatter.formatRoom(user))
                .tenantName(resolveTenantName(user))
                .docImages(docImages)
                .rejectReason(user.getRejectReason())
                .bannedReason(user.getBannedReason())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String resolveTenantName(User user) {
        try {
            if (user.getRoom() != null
                    && user.getRoom().getUnit() != null
                    && user.getRoom().getUnit().getBuilding() != null) {
                Long tenantId = user.getRoom().getUnit().getBuilding().getTenantId();
                if (tenantId != null) {
                    return tenantRepository.findById(tenantId)
                            .map(t -> t.getName())
                            .orElse("");
                }
            }
        } catch (Exception e) {
            log.debug("解析租户名称失败", e);
        }
        return "";
    }

    private OperationLogDTO toOperationLogDTO(OperationLog log) {
        User admin = userRepository.findById(log.getAdminId()).orElse(null);
        return OperationLogDTO.builder()
                .id(log.getId())
                .adminId(log.getAdminId())
                .adminName(admin != null ? admin.getName() : "未知管理员")
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .detail(log.getDetail())
                .createdAt(log.getCreatedAt())
                .build();
    }

    // ==================== 私有辅助方法：内容转换 ====================

    /**
     * 将 IdleItem 实体转换为包含完整对方/评价/违规信息的 ContentItemDTO。
     */
    /**
     * 将 IdleItem 实体转换为包含完整对方/评价/违规信息的 ContentItemDTO。
     */
    private ContentItemDTO toContentItemDTO(IdleItem item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);
        String violatorName = getViolatorName(item.getViolatedBy());

        ContentItemDTO.ContentItemDTOBuilder builder = ContentItemDTO.builder()
                .id(item.getId())
                .type("idle")
                .title(item.getTitle())
                .description(item.getDescription())
                .images(parseImages(item.getImages()))
                .category(item.getCategory())
                .price(item.getPrice())
                .condition(item.getCondition())
                .publisherName(user != null ? user.getName() : "未知用户")
                .publisherRoom(user != null ? UserFormatter.formatRoomWithType(user) : "")
                .displayStatus(displayStatus(item.getStatus()))
                .rawStatus(item.getStatus())
                .isProxy(item.getIsProxy())
                .createdAt(item.getCreatedAt())
                .maxDuration(item.getMaxDuration())
                .durationUnit(item.getDurationUnit())
                .violationType(item.getViolationType())
                .violationReason(item.getViolationReason())
                .violatorName(violatorName)
                .violatedAt(item.getViolatedAt())
                .building(user != null ? getBuildingName(user) : null);

        // 根据状态填充对方信息、评价和时间线
        if ("borrowing".equals(item.getStatus())) {
            fillBorrowingPeerInfo(item, builder);
        } else if (BizStatus.COMPLETED.equals(item.getStatus())) {
            fillCompletedPeerInfo(item, builder);
        }

        return builder.build();
    }

    /**
     * 填充借用中状态的对方信息和时间范围到 builder。
     */
    private void fillBorrowingPeerInfo(IdleItem item, ContentItemDTO.ContentItemDTOBuilder builder) {
        BorrowRequest activeBorrow = findActiveBorrowRequest(item.getId());
        if (activeBorrow == null) {
            return;
        }
        User borrower = userRepository.findById(activeBorrow.getBorrowerId()).orElse(null);
        if (borrower != null) {
            builder.peerName(borrower.getName());
            builder.peerRoom(UserFormatter.formatRoomWithType(borrower));
        }
        if (activeBorrow.getStartDate() != null) {
            builder.timeStart(activeBorrow.getStartDate().atStartOfDay());
            if (activeBorrow.getDurationDays() != null) {
                builder.timeEnd(activeBorrow.getStartDate().plusDays(activeBorrow.getDurationDays()).atStartOfDay());
            }
        }
    }

    /**
     * 填充已完成状态的对方信息、评价和时间线到 builder。
     */
    private void fillCompletedPeerInfo(IdleItem item, ContentItemDTO.ContentItemDTOBuilder builder) {
        BorrowRequest completedBorrow = findCompletedBorrowRequest(item.getId());
        if (completedBorrow == null) {
            return;
        }
        User borrower = userRepository.findById(completedBorrow.getBorrowerId()).orElse(null);
        if (borrower != null) {
            builder.peerName(borrower.getName());
            builder.peerRoom(UserFormatter.formatRoomWithType(borrower));
            builder.peerRating(ratingRepository.getAverageScore(borrower.getId()));
        }

        // 时间范围
        LocalDateTime timeStart = null;
        if (completedBorrow.getStartDate() != null) {
            timeStart = completedBorrow.getStartDate().atStartOfDay();
            builder.timeStart(timeStart);
            if (completedBorrow.getDurationDays() != null) {
                builder.timeEnd(completedBorrow.getStartDate().plusDays(completedBorrow.getDurationDays()).atStartOfDay());
            }
        }

        // 发布者评价：借入方对物主的评价
        Rating pubRating = ratingRepository
                .findByBorrowIdAndFromUserId(completedBorrow.getId(), completedBorrow.getBorrowerId())
                .orElse(null);
        if (pubRating != null) {
            builder.publisherRatingScore((double) pubRating.getScore());
            builder.publisherRatingStars(scoreToStars(pubRating.getScore()));
            builder.publisherRatingComment(null);
        }

        // 对方评价：物主对借入方的评价
        Rating peerR = ratingRepository
                .findByBorrowIdAndFromUserId(completedBorrow.getId(), item.getUserId())
                .orElse(null);
        if (peerR != null) {
            builder.peerRatingScore((double) peerR.getScore());
            builder.peerRatingStars(scoreToStars(peerR.getScore()));
            builder.peerRatingComment(null);
        }

        // 时间线：借入申请 → 同意借出 → 已完成
        builder.applyAt(completedBorrow.getCreatedAt());
        builder.approveAt(timeStart != null ? timeStart : completedBorrow.getCreatedAt());
        builder.completeAt(completedBorrow.getUpdatedAt());
    }

    /**
     * 将 HelpRequest 实体转换为包含完整对方/评价/违规信息的 ContentItemDTO。
     */
    private ContentItemDTO toContentItemDTO(HelpRequest item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);
        String violatorName = getViolatorName(item.getViolatedBy());

        ContentItemDTO.ContentItemDTOBuilder builder = ContentItemDTO.builder()
                .id(item.getId())
                .type("help")
                .title(item.getTitle())
                .description(item.getDescription())
                .images(parseImages(item.getImages()))
                .category(item.getCategory())
                .publisherName(user != null ? user.getName() : "未知用户")
                .publisherRoom(user != null ? UserFormatter.formatRoomWithType(user) : "")
                .displayStatus(displayStatus(item.getStatus()))
                .rawStatus(item.getStatus())
                .isProxy(item.getIsProxy())
                .isUrgent(item.getIsUrgent())
                .createdAt(item.getCreatedAt())
                .timeStart(item.getTimeStart())
                .timeEnd(item.getTimeEnd())
                .violationType(item.getViolationType())
                .violationReason(item.getViolationReason())
                .violatorName(violatorName)
                .violatedAt(item.getViolatedAt())
                .building(user != null ? getBuildingName(user) : null);

        // 根据状态填充对方信息、评价和时间线
        if ("helping".equals(item.getStatus())) {
            fillHelpingPeerInfo(item, builder);
        } else if (BizStatus.COMPLETED.equals(item.getStatus())) {
            fillHelpCompletedPeerInfo(item, builder);
        }

        return builder.build();
    }

    /**
     * 填充帮助中状态的对方信息到 builder。
     */
    private void fillHelpingPeerInfo(HelpRequest item, ContentItemDTO.ContentItemDTOBuilder builder) {
        HelpApplication activeApp = findActiveHelpApplication(item.getId());
        if (activeApp == null) {
            return;
        }
        User helper = userRepository.findById(activeApp.getHelperId()).orElse(null);
        if (helper != null) {
            builder.peerName(helper.getName());
            builder.peerRoom(UserFormatter.formatRoomWithType(helper));
        }
    }

    /**
     * 填充帮助完成状态的对方信息、评价和时间线到 builder。
     */
    private void fillHelpCompletedPeerInfo(HelpRequest item, ContentItemDTO.ContentItemDTOBuilder builder) {
        HelpApplication completedApp = findCompletedHelpApplication(item.getId());
        if (completedApp == null) {
            return;
        }
        User helper = userRepository.findById(completedApp.getHelperId()).orElse(null);
        if (helper != null) {
            builder.peerName(helper.getName());
            builder.peerRoom(UserFormatter.formatRoomWithType(helper));
            builder.peerRating(ratingRepository.getAverageScore(helper.getId()));
        }

        // 发布者评价：帮助者对求助者的评价
        Rating pubRating = ratingRepository
                .findByHelpApplicationIdAndFromUserId(completedApp.getId(), completedApp.getHelperId())
                .orElse(null);
        if (pubRating != null) {
            builder.publisherRatingScore((double) pubRating.getScore());
            builder.publisherRatingStars(scoreToStars(pubRating.getScore()));
            builder.publisherRatingComment(null);
        }

        // 对方评价：求助者对帮助者的评价
        Rating peerR = ratingRepository
                .findByHelpApplicationIdAndFromUserId(completedApp.getId(), item.getUserId())
                .orElse(null);
        if (peerR != null) {
            builder.peerRatingScore((double) peerR.getScore());
            builder.peerRatingStars(scoreToStars(peerR.getScore()));
            builder.peerRatingComment(null);
        }

        // 时间线：帮忙申请 → 同意帮忙 → 已完成
        builder.applyAt(completedApp.getCreatedAt());
        builder.approveAt(item.getTimeStart() != null ? item.getTimeStart() : completedApp.getCreatedAt());
        builder.completeAt(completedApp.getCompletedAt());
    }

    // ==================== 私有辅助方法：数据查询 ====================

    /**
     * 将 JSON 数组格式的图片字符串解析为 URL 列表。
     * 值为 null/空白或非法 JSON 时返回空列表。
     * 同时过滤掉解析结果中的空白/空 URL。
     */
    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            List<String> urls = OBJECT_MAPPER.readValue(imagesJson, new TypeReference<List<String>>() {});
            return urls.stream()
                    .filter(url -> url != null && !url.isBlank())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 按状态、楼栋用户 ID 和搜索关键词筛选查询闲置物品。
     */
    private List<IdleItem> fetchIdleItems(List<String> statuses, List<Long> buildingUserIds, Long tenantId, String search) {
        List<IdleItem> allItems = new ArrayList<>();
        for (String status : statuses) {
            allItems.addAll(idleItemRepository.findByStatus(status));
        }

        // 应用租户过滤
        allItems = allItems.stream()
                .filter(i -> tenantId.equals(i.getTenantId()))
                .collect(Collectors.toList());

        // 应用楼栋过滤
        if (buildingUserIds != null) {
            allItems = allItems.stream()
                    .filter(i -> buildingUserIds.contains(i.getUserId()))
                    .collect(Collectors.toList());
        }

        // 应用搜索过滤
        if (search != null && !search.isEmpty()) {
            String keyword = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(i -> i.getTitle() != null && i.getTitle().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
        }

        return allItems;
    }

    /**
     * 按状态、楼栋用户 ID 和搜索关键词筛选查询求助信息。
     */
    private List<HelpRequest> fetchHelpItems(List<String> statuses, List<Long> buildingUserIds, Long tenantId, String search) {
        List<HelpRequest> allItems = new ArrayList<>();
        for (String status : statuses) {
            allItems.addAll(helpRequestRepository.findByStatus(status));
        }

        // 应用租户过滤
        allItems = allItems.stream()
                .filter(h -> tenantId.equals(h.getTenantId()))
                .collect(Collectors.toList());

        // 应用楼栋过滤
        if (buildingUserIds != null) {
            allItems = allItems.stream()
                    .filter(h -> buildingUserIds.contains(h.getUserId()))
                    .collect(Collectors.toList());
        }

        // 应用搜索过滤
        if (search != null && !search.isEmpty()) {
            String keyword = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(h -> h.getTitle() != null && h.getTitle().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
        }

        return allItems;
    }

    private BorrowRequest findActiveBorrowRequest(Long idleId) {
        List<BorrowRequest> borrows = borrowRequestRepository.findByIdleId(idleId);
        return borrows.stream()
                .filter(b -> BizStatus.ACTIVE.equals(b.getStatus()) || BizStatus.APPROVED.equals(b.getStatus()))
                .findFirst().orElse(null);
    }

    private BorrowRequest findCompletedBorrowRequest(Long idleId) {
        List<BorrowRequest> borrows = borrowRequestRepository.findByIdleId(idleId);
        return borrows.stream()
                .filter(b -> BizStatus.RETURNED.equals(b.getStatus()))
                .findFirst().orElse(null);
    }

    private HelpApplication findActiveHelpApplication(Long helpId) {
        List<HelpApplication> apps = helpApplicationRepository.findByHelpId(helpId);
        return apps.stream()
                .filter(a -> BizStatus.APPROVED.equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    private HelpApplication findCompletedHelpApplication(Long helpId) {
        List<HelpApplication> apps = helpApplicationRepository.findByHelpId(helpId);
        return apps.stream()
                .filter(a -> BizStatus.COMPLETED.equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    // ==================== 私有辅助方法：楼栋解析 ====================

    /**
     * 将楼栋名称解析为居住在该楼栋的用户 ID 列表。
     */
    private List<Long> resolveBuildingUserIds(String buildingName, String unitName) {
        List<Building> allBuildings = buildingRepository.findAll();
        Building targetBuilding = allBuildings.stream()
                .filter(b -> b.getName() != null && b.getName().contains(buildingName))
                .findFirst().orElse(null);
        if (targetBuilding == null) {
            return new ArrayList<>();
        }

        List<Unit> units = unitRepository.findByBuildingId(targetBuilding.getId());
        // 若指定了单元则过滤：直接字符串匹配（单元名使用阿拉伯数字："1单元"/"2单元"）
        if (unitName != null && !unitName.isEmpty()) {
            units = units.stream()
                    .filter(u -> u.getName() != null && u.getName().contains(unitName))
                    .collect(Collectors.toList());
        }

        List<Long> roomIds = new ArrayList<>();
        for (Unit unit : units) {
            List<Room> rooms = roomRepository.findByUnitId(unit.getId());
            roomIds.addAll(rooms.stream().map(Room::getId).collect(Collectors.toList()));
        }

        if (roomIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<User> users = userRepository.findByRoomIdIn(roomIds);
        return users.stream().map(User::getId).collect(Collectors.toList());
    }

    // ==================== 私有辅助方法：状态映射 ====================

    /**
     * 将 UI 状态页签映射为 IdleItem 的数据库状态值列表。
     */
    private List<String> mapStatusTabToIdleStatuses(String statusTab) {
        switch (statusTab) {
            case "showing":
                return java.util.Collections.singletonList(BizStatus.ONLINE);
            case "progressing":
                return java.util.Collections.singletonList("borrowing");
            case "completed":
                return java.util.Collections.singletonList(BizStatus.COMPLETED);
            case "violation":
                return java.util.Collections.singletonList("deleted");
            case "all":
            default:
                return java.util.Arrays.asList(BizStatus.ONLINE, "borrowing", BizStatus.COMPLETED, "deleted");
        }
    }

    /**
     * 将 UI 状态页签映射为 HelpRequest 的数据库状态值列表。
     */
    private List<String> mapStatusTabToHelpStatuses(String statusTab) {
        switch (statusTab) {
            case "showing":
                return java.util.Collections.singletonList(BizStatus.ONLINE);
            case "progressing":
                return java.util.Collections.singletonList("helping");
            case "completed":
                return java.util.Collections.singletonList(BizStatus.COMPLETED);
            case "violation":
                return java.util.Collections.singletonList("deleted");
            case "all":
            default:
                return java.util.Arrays.asList(BizStatus.ONLINE, "helping", BizStatus.COMPLETED, "deleted");
        }
    }

    /**
     * 将数据库原始状态映射为中文显示状态。
     */
    private String displayStatus(String rawStatus) {
        if (rawStatus == null) return "未知";
        switch (rawStatus) {
            case BizStatus.ONLINE:
                return "展示中";
            case "borrowing":
            case "helping":
                return "进行中";
            case BizStatus.COMPLETED:
                return "已完成";
            case "deleted":
                return "已下架";
            default:
                return rawStatus;
        }
    }

    /**
     * 从用户的房间关联链中提取楼栋名称。
     */
    private String getBuildingName(User user) {
        if (user == null) return null;
        try {
            if (user.getRoom() != null
                    && user.getRoom().getUnit() != null
                    && user.getRoom().getUnit().getBuilding() != null) {
                return user.getRoom().getUnit().getBuilding().getName();
            }
        } catch (Exception e) {
            log.debug("解析楼栋名称失败", e);
        }
        return null;
    }

    /**
     * 将数字评分（1-5）转换为星级字符串。
     */
    private String scoreToStars(int score) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < score ? "★" : "☆");
        }
        return sb.toString();
    }

    /**
     * 从形如 "3栋"、"2单元"、"1502号" 的字符串中提取前面的数字。
     */
    private int extractNumber(String s) {
        if (s == null) return 0;
        String num = s.replaceAll("[^0-9]", "");
        try {
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 手机号脱敏，仅显示前 3 位和后 4 位。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 若姓名为纯 ASCII（如英文微信昵称）则脱敏，
     * 避免在管理端住户列表中显示非中文姓名。
     */
    private String maskName(String name) {
        if (name == null || name.isEmpty()) return "——";
        // 姓名为纯 ASCII 时返回占位符
        if (name.matches("[\\p{ASCII}\\s]+")) return "——";
        return name;
    }

    /**
     * 按 ID 查询违规处理人姓名（用于没有 @ManyToOne violator 字段的 HelpRequest）。
     */
    private String getViolatorName(Long violatedBy) {
        if (violatedBy == null) return null;
        return userRepository.findById(violatedBy).map(User::getName).orElse(null);
    }

    // ==================== 私有辅助方法：通知 ====================

    private void createNotification(Long userId, String type, String title, String content, Long relatedId) {
        notificationService.create(userId, type, title, content, relatedId);
    }
}
