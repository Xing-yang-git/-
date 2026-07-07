package com.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.model.dto.AuditRequest;
import com.platform.model.dto.ContentItemDTO;
import com.platform.model.dto.ContentOfflineRequest;
import com.platform.model.dto.DashboardDTO;
import com.platform.model.dto.HelpRequestDTO;
import com.platform.model.dto.HelpResponseDTO;
import com.platform.model.dto.IdleItemDTO;
import com.platform.model.dto.IdleItemRequest;
import com.platform.model.dto.NotificationDTO;
import com.platform.model.dto.OperationLogDTO;
import com.platform.model.dto.PageDTO;
import com.platform.model.dto.ResidentDTO;
import com.platform.model.dto.UserDTO;
import com.platform.model.entity.BorrowRequest;
import com.platform.model.entity.Building;
import com.platform.model.entity.ChatSession;
import com.platform.model.entity.HelpApplication;
import com.platform.model.entity.HelpRequest;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.Notification;
import com.platform.model.entity.OperationLog;
import com.platform.model.entity.Rating;
import com.platform.model.entity.Room;
import com.platform.model.entity.Unit;
import com.platform.model.entity.User;
import com.platform.repository.BorrowRequestRepository;
import com.platform.repository.BuildingRepository;
import com.platform.repository.ChatMessageRepository;
import com.platform.repository.ChatSessionRepository;
import com.platform.repository.HelpApplicationRepository;
import com.platform.repository.HelpRequestRepository;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.NotificationRepository;
import com.platform.repository.OperationLogRepository;
import com.platform.repository.RatingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.TenantRepository;
import com.platform.repository.UnitRepository;
import com.platform.repository.UserRepository;
import com.platform.repository.VerificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IdleItemRepository idleItemRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final HelpApplicationRepository helpApplicationRepository;
    private final UserRepository userRepository;
    private final VerificationRepository verificationRepository;
    private final OperationLogRepository operationLogRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationRepository notificationRepository;
    private final TenantRepository tenantRepository;
    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final RatingRepository ratingRepository;

    public AdminService(IdleItemRepository idleItemRepository,
                        HelpRequestRepository helpRequestRepository,
                        BorrowRequestRepository borrowRequestRepository,
                        HelpApplicationRepository helpApplicationRepository,
                        UserRepository userRepository,
                        VerificationRepository verificationRepository,
                        OperationLogRepository operationLogRepository,
                        ChatSessionRepository chatSessionRepository,
                        ChatMessageRepository chatMessageRepository,
                        NotificationRepository notificationRepository,
                        TenantRepository tenantRepository,
                        BuildingRepository buildingRepository,
                        UnitRepository unitRepository,
                        RoomRepository roomRepository,
                        RatingRepository ratingRepository) {
        this.idleItemRepository = idleItemRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.helpApplicationRepository = helpApplicationRepository;
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
        this.operationLogRepository = operationLogRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.notificationRepository = notificationRepository;
        this.tenantRepository = tenantRepository;
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.roomRepository = roomRepository;
        this.ratingRepository = ratingRepository;
    }

    // ==================== Dashboard ====================

    public DashboardDTO getDashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                .withHour(23).withMinute(59).withSecond(59).withNano(999999999);

        List<IdleItem> allIdle = idleItemRepository.findAll();
        long onlineLendCount = allIdle.stream()
                .filter(i -> "online".equals(i.getStatus()) && "LEND".equals(i.getPostType()))
                .count();
        long onlineWantedCount = allIdle.stream()
                .filter(i -> "online".equals(i.getStatus()) && "WANTED".equals(i.getPostType()))
                .count();

        List<HelpRequest> allHelp = helpRequestRepository.findAll();
        long onlineHelpCount = allHelp.stream()
                .filter(h -> "online".equals(h.getStatus()))
                .count();

        long monthlyIdlePublishes = allIdle.stream()
                .filter(i -> i.getCreatedAt() != null
                        && !i.getCreatedAt().isBefore(monthStart)
                        && !i.getCreatedAt().isAfter(monthEnd))
                .count();
        long monthlyHelpPublishes = allHelp.stream()
                .filter(h -> h.getCreatedAt() != null
                        && !h.getCreatedAt().isBefore(monthStart)
                        && !h.getCreatedAt().isAfter(monthEnd))
                .count();

        List<BorrowRequest> allBorrows = borrowRequestRepository.findAll();
        long monthlyCompletedBorrows = allBorrows.stream()
                .filter(b -> "returned".equals(b.getStatus())
                        && b.getCreatedAt() != null
                        && !b.getCreatedAt().isBefore(monthStart)
                        && !b.getCreatedAt().isAfter(monthEnd))
                .count();

        long monthlyTotalBorrows = allBorrows.stream()
                .filter(b -> b.getCreatedAt() != null
                        && !b.getCreatedAt().isBefore(monthStart)
                        && !b.getCreatedAt().isAfter(monthEnd))
                .count();
        double completionRate = monthlyTotalBorrows > 0
                ? (double) monthlyCompletedBorrows / monthlyTotalBorrows : 0.0;

        long monthlyActiveUsers = userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null
                        && !u.getCreatedAt().isBefore(monthStart)
                        && !u.getCreatedAt().isAfter(monthEnd))
                .count();

        long damageCount = allBorrows.stream()
                .filter(b -> b.getDamageType() != null && !"none".equals(b.getDamageType()))
                .count();

        Map<String, Long> categoryCountMap = allIdle.stream()
                .filter(i -> "online".equals(i.getStatus()))
                .collect(Collectors.groupingBy(
                        i -> i.getCategory() != null ? i.getCategory() : "其他",
                        Collectors.counting()));
        List<DashboardDTO.CategoryStat> categoryStats = categoryCountMap.entrySet().stream()
                .map(e -> DashboardDTO.CategoryStat.builder()
                        .category(e.getKey())
                        .count(e.getValue())
                        .build())
                .collect(Collectors.toList());

        List<DashboardDTO.ItemStat> itemStats = new ArrayList<>();
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线闲置(LEND)").value(onlineLendCount).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线闲置(WANTED)").value(onlineWantedCount).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("在线求助").value(onlineHelpCount).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("本月发布").value(monthlyIdlePublishes + monthlyHelpPublishes).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("本月完成借入").value(monthlyCompletedBorrows).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("月活用户").value(monthlyActiveUsers).build());
        itemStats.add(DashboardDTO.ItemStat.builder().label("损坏物品").value(damageCount).build());

        return DashboardDTO.builder()
                .onlineIdleCount(onlineLendCount + onlineWantedCount)
                .onlineHelpCount(onlineHelpCount)
                .monthlyPublishes(monthlyIdlePublishes + monthlyHelpPublishes)
                .monthlyCompletedBorrows(monthlyCompletedBorrows)
                .completionRate(Math.round(completionRate * 1000.0) / 1000.0)
                .monthlyActiveUsers(monthlyActiveUsers)
                .damageCount(damageCount)
                .categoryStats(categoryStats)
                .itemStats(itemStats)
                .build();
    }

    // ==================== Audits ====================

    /**
     * Get audits list filtered by auth status.
     * @param status  "pending" / "approved" / "rejected" / null (all non-registering)
     * @param page    zero-based page index
     * @param size    page size
     */
    public PageDTO<UserDTO> getAudits(String status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage;

        if (status != null && !status.isEmpty()) {
            userPage = userRepository.findByAuthStatus(status, pageRequest);
        } else {
            // "all" tab: exclude users still in "registering" state (haven't completed registration)
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
     * Get counts for each audit tab.
     */
    public Map<String, Long> getAuditCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("pending", userRepository.countByAuthStatus("pending"));
        counts.put("approved", userRepository.countByAuthStatus("approved"));
        counts.put("rejected", userRepository.countByAuthStatus("rejected"));
        counts.put("all", userRepository.countByAuthStatusNot("registering"));
        return counts;
    }

    public Map<String, Object> auditUser(UUID adminId, UUID userId, AuditRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        user.setAuthStatus(req.getApproved() ? "approved" : "rejected");
        if (req.getApproved()) {
            user.setRejectReason(null);
        } else {
            user.setRejectReason(req.getReason());
        }
        userRepository.save(user);

        String title = req.getApproved() ? "实名认证已通过" : "实名认证被拒绝";
        String content = req.getApproved()
                ? "您的实名认证申请已通过审核"
                : "您的实名认证申请被拒绝"
                        + (req.getReason() != null ? "，原因：" + req.getReason() : "");
        createNotification(userId, "audit_result", title, content, null);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
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

    // ==================== Content Management ====================

    /**
     * Get paginated content list filtered by status tab, type, building, and search keyword.
     *
     * @param statusTab "showing"|"progressing"|"completed"|"violation"|"all"
     * @param type      "idle"|"help"|null (both)
     * @param building  e.g. "3栋" | null
     * @param search    keyword | null
     * @param page      zero-based page index
     * @param size      page size
     * @return paginated content items
     */
    public PageDTO<ContentItemDTO> getContentList(String statusTab, String type, String building,
                                                   String search, int page, int size) {
        // Resolve building filter to user IDs
        List<UUID> buildingUserIds = null;
        if (building != null && !building.isEmpty()) {
            buildingUserIds = resolveBuildingUserIds(building);
            if (buildingUserIds.isEmpty()) {
                return PageDTO.<ContentItemDTO>builder()
                        .content(new ArrayList<>())
                        .totalElements(0)
                        .totalPages(0)
                        .currentPage(page)
                        .size(size)
                        .build();
            }
        }

        // Map status tab to DB status lists
        String effectiveTab = (statusTab != null && !statusTab.isEmpty()) ? statusTab : "all";
        List<String> idleStatuses = mapStatusTabToIdleStatuses(effectiveTab);
        List<String> helpStatuses = mapStatusTabToHelpStatuses(effectiveTab);

        List<ContentItemDTO> allItems = new ArrayList<>();

        // Fetch idle items
        if (type == null || "idle".equals(type)) {
            List<IdleItem> idleItems = fetchIdleItems(idleStatuses, buildingUserIds, search);
            for (IdleItem item : idleItems) {
                allItems.add(toContentItemDTO(item));
            }
        }

        // Fetch help items
        if (type == null || "help".equals(type)) {
            List<HelpRequest> helpItems = fetchHelpItems(helpStatuses, buildingUserIds, search);
            for (HelpRequest item : helpItems) {
                allItems.add(toContentItemDTO(item));
            }
        }

        // Sort by createdAt DESC
        allItems.sort((a, b) -> {
            LocalDateTime ta = a.getCreatedAt();
            LocalDateTime tb = b.getCreatedAt();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        // Paginate
        int totalElements = allItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ContentItemDTO> pageContent = fromIndex < totalElements
                ? new ArrayList<>(allItems.subList(fromIndex, toIndex))
                : new ArrayList<>();

        return PageDTO.<ContentItemDTO>builder()
                .content(pageContent)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .build();
    }

    /**
     * Get detailed content info including peer info, ratings, and violation info.
     *
     * @param id   content ID
     * @param type "idle" or "help"
     * @return detailed content item DTO
     */
    public ContentItemDTO getContentDetail(UUID id, String type) {
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
     * Get counts for each content status tab.
     *
     * @return map of tab name to count
     */
    public Map<String, Long> getContentCounts() {
        Map<String, Long> counts = new HashMap<>();

        long idleShowing = idleItemRepository.countByStatus("online");
        long helpShowing = helpRequestRepository.countByStatus("online");
        counts.put("showing", idleShowing + helpShowing);

        long idleProgressing = idleItemRepository.countByStatus("borrowing");
        long helpProgressing = helpRequestRepository.countByStatus("helping");
        counts.put("progressing", idleProgressing + helpProgressing);

        long idleCompleted = idleItemRepository.countByStatus("completed");
        long helpCompleted = helpRequestRepository.countByStatus("completed");
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
     * Offline (remove) content with violation info.
     *
     * @param adminId   admin performing the action
     * @param contentId content ID to remove
     * @param req       offline request with type, reasons, and custom reason
     * @return result map
     */
    public Map<String, Object> removeContent(UUID adminId, UUID contentId, ContentOfflineRequest req) {
        String violationType = req.getReasons() != null && !req.getReasons().isEmpty()
                ? String.join("，", req.getReasons())
                : "违规";
        String violationReason = req.getCustomReason() != null && !req.getCustomReason().isEmpty()
                ? req.getCustomReason()
                : violationType;

        if ("idle".equals(req.getTargetType())) {
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

            OperationLog log = new OperationLog();
            log.setAdminId(adminId);
            log.setAction("remove_content");
            log.setTargetType("idle");
            log.setTargetId(contentId);
            log.setDetail(violationReason);
            log.setCreatedAt(LocalDateTime.now());
            operationLogRepository.save(log);

        } else if ("help".equals(req.getTargetType())) {
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

            OperationLog log = new OperationLog();
            log.setAdminId(adminId);
            log.setAction("remove_content");
            log.setTargetType("help");
            log.setTargetId(contentId);
            log.setDetail(violationReason);
            log.setCreatedAt(LocalDateTime.now());
            operationLogRepository.save(log);

        } else {
            throw new RuntimeException("不支持的目标类型");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "内容已删除");
        return result;
    }

    // ==================== Proxy Publishing ====================

    public IdleItemDTO proxyPublishIdle(UUID adminId, IdleItemRequest req) {
        IdleItem item = new IdleItem();
        item.setUserId(req.getUserId() != null ? req.getUserId() : adminId);
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setPostType(req.getPostType());
        item.setCategory(req.getCategory());
        item.setImages(req.getImages());
        item.setPrice(req.getPrice());
        item.setStatus("online");
        item.setIsProxy(true);
        item.setCreatedAt(LocalDateTime.now());
        item = idleItemRepository.save(item);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
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

    public HelpResponseDTO proxyPublishHelp(UUID adminId, HelpRequestDTO req) {
        HelpRequest helpRequest = new HelpRequest();
        helpRequest.setUserId(req.getUserId() != null ? req.getUserId() : adminId);
        helpRequest.setTitle(req.getTitle());
        helpRequest.setDescription(req.getDescription());
        helpRequest.setCategory(req.getCategory());
        helpRequest.setIsUrgent(req.getIsUrgent() != null && req.getIsUrgent());
        helpRequest.setStatus("online");
        helpRequest.setIsProxy(true);
        helpRequest.setCreatedAt(LocalDateTime.now());
        helpRequest = helpRequestRepository.save(helpRequest);

        OperationLog log = new OperationLog();
        log.setAdminId(adminId);
        log.setAction("proxy_publish_help");
        log.setTargetType("help");
        log.setTargetId(helpRequest.getId());
        log.setDetail("管理员代发求助");
        log.setCreatedAt(LocalDateTime.now());
        operationLogRepository.save(log);

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

    // ==================== Records ====================

    public PageDTO<Map<String, Object>> getRecords(String type, int page, int size) {
        List<Map<String, Object>> allRecords = new ArrayList<>();

        if ("borrow".equals(type)) {
            List<BorrowRequest> borrows = borrowRequestRepository.findByStatus("returned");
            for (BorrowRequest br : borrows) {
                IdleItem idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
                User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);
                UUID ownerId = idleItem != null ? idleItem.getUserId() : null;
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
                allRecords.add(map);
            }
        } else if ("help".equals(type)) {
            List<HelpApplication> applications = helpApplicationRepository.findByStatus("completed");
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
                allRecords.add(map);
            }
        } else {
            throw new RuntimeException("不支持的类型，请使用 borrow 或 help");
        }

        allRecords.sort((a, b) -> {
            LocalDateTime ta = (LocalDateTime) a.get("createdAt");
            LocalDateTime tb = (LocalDateTime) b.get("createdAt");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        int totalElements = allRecords.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Map<String, Object>> pageContent = fromIndex < totalElements
                ? allRecords.subList(fromIndex, toIndex)
                : new ArrayList<>();

        return PageDTO.<Map<String, Object>>builder()
                .content(pageContent)
                .totalElements((long) totalElements)
                .totalPages(totalPages)
                .currentPage(page)
                .size(size)
                .build();
    }

    // ==================== Operation Logs ====================

    public PageDTO<OperationLogDTO> getOperationLogs(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OperationLog> logPage = operationLogRepository.findAll(pageRequest);

        List<OperationLogDTO> dtos = logPage.getContent().stream()
                .map(this::toOperationLogDTO)
                .collect(Collectors.toList());

        return PageDTO.<OperationLogDTO>builder()
                .content(dtos)
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    // ==================== Export ====================

    public List<Map<String, Object>> exportData(String type) {
        List<Map<String, Object>> data = new ArrayList<>();

        if ("idle".equals(type)) {
            List<IdleItem> items = idleItemRepository.findAll();
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
        } else if ("help".equals(type)) {
            List<HelpRequest> items = helpRequestRepository.findAll();
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
        } else if ("borrow".equals(type)) {
            List<BorrowRequest> borrows = borrowRequestRepository.findAll();
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
        }

        return data;
    }

    // ==================== Chat Sessions ====================

    public List<Map<String, Object>> getChatSessions(String keyword) {
        List<ChatSession> sessions;
        if (keyword != null && !keyword.isEmpty()) {
            List<ChatSession> allSessions = chatSessionRepository.findAll();
            sessions = allSessions.stream()
                    .filter(s -> {
                        User user1 = userRepository.findById(s.getUser1Id()).orElse(null);
                        User user2 = userRepository.findById(s.getUser2Id()).orElse(null);
                        String name1 = user1 != null ? user1.getName() : "";
                        String name2 = user2 != null ? user2.getName() : "";
                        return name1.contains(keyword) || name2.contains(keyword);
                    })
                    .collect(Collectors.toList());
        } else {
            sessions = chatSessionRepository.findAll();
        }

        return sessions.stream().map(s -> {
            User user1 = userRepository.findById(s.getUser1Id()).orElse(null);
            User user2 = userRepository.findById(s.getUser2Id()).orElse(null);

            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("postType", s.getPostType());
            map.put("postId", s.getPostId());
            map.put("user1Name", user1 != null ? user1.getName() : "未知用户");
            map.put("user2Name", user2 != null ? user2.getName() : "未知用户");
            map.put("createdAt", s.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    // ==================== Buildings ====================

    /**
     * Get all building names.
     *
     * @return list of building info maps
     */
    public List<Map<String, Object>> getBuildings() {
        List<Building> buildings = buildingRepository.findAll();
        return buildings.stream().map(b -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", b.getId());
            map.put("name", b.getName());
            return map;
        }).collect(Collectors.toList());
    }

    // ==================== Resident Search ====================

    /**
     * Search residents with filters.
     *
     * @param building building name filter (partial match)
     * @param unit     unit name filter (partial match)
     * @param room     room number filter (partial match)
     * @param userType "业主" | "租客" | null
     * @param keyword  name or phone keyword
     * @param page     zero-based page index
     * @param size     page size
     * @return paginated resident DTOs
     */
    public PageDTO<ResidentDTO> searchResidents(String building, String unit, String room,
                                                 String userType, String keyword,
                                                 int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findResidents(building, unit, room, userType, keyword, pageRequest);

        List<ResidentDTO> dtos = userPage.getContent().stream()
                .map(u -> ResidentDTO.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .room(formatRoom(u))
                        .userType(u.getUserType())
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

    // ==================== Private Helpers: DTO Conversion ====================

    private UserDTO toUserDTO(User user) {
        java.util.List<String> docImages = java.util.Collections.emptyList();
        if (user.getDocImages() != null && !user.getDocImages().isEmpty()) {
            try {
                docImages = objectMapper.readValue(user.getDocImages(), new TypeReference<java.util.List<String>>() {});
            } catch (Exception e) {
                // ignore parse errors
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
                .userRoom(formatRoom(user))
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
                java.util.UUID tenantId = user.getRoom().getUnit().getBuilding().getTenantId();
                if (tenantId != null) {
                    return tenantRepository.findById(tenantId)
                            .map(t -> t.getName())
                            .orElse("");
                }
            }
        } catch (Exception e) {
            // ignore
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

    // ==================== Private Helpers: Content Conversion ====================

    /**
     * Convert an IdleItem entity to a ContentItemDTO with full peer/rating/violation info.
     */
    private ContentItemDTO toContentItemDTO(IdleItem item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);

        // Peer info from related BorrowRequest
        String peerName = null;
        String peerRoom = null;
        Double peerRating = null;
        String publisherRatingStars = null;
        Double publisherRatingScore = null;
        String peerRatingStars = null;
        Double peerRatingScore = null;
        LocalDateTime timeStart = null;
        LocalDateTime timeEnd = null;

        if ("borrowing".equals(item.getStatus())) {
            BorrowRequest activeBorrow = findActiveBorrowRequest(item.getId());
            if (activeBorrow != null) {
                User borrower = userRepository.findById(activeBorrow.getBorrowerId()).orElse(null);
                if (borrower != null) {
                    peerName = borrower.getName();
                    peerRoom = formatRoomWithType(borrower);
                }
                if (activeBorrow.getStartDate() != null) {
                    timeStart = activeBorrow.getStartDate().atStartOfDay();
                    if (activeBorrow.getDurationDays() != null) {
                        timeEnd = activeBorrow.getStartDate().plusDays(activeBorrow.getDurationDays()).atStartOfDay();
                    }
                }
            }
        } else if ("completed".equals(item.getStatus())) {
            BorrowRequest completedBorrow = findCompletedBorrowRequest(item.getId());
            if (completedBorrow != null) {
                User borrower = userRepository.findById(completedBorrow.getBorrowerId()).orElse(null);
                if (borrower != null) {
                    peerName = borrower.getName();
                    peerRoom = formatRoomWithType(borrower);
                    peerRating = ratingRepository.getAverageScore(borrower.getId());
                }
                if (completedBorrow.getStartDate() != null) {
                    timeStart = completedBorrow.getStartDate().atStartOfDay();
                    if (completedBorrow.getDurationDays() != null) {
                        timeEnd = completedBorrow.getStartDate().plusDays(completedBorrow.getDurationDays()).atStartOfDay();
                    }
                }
                // Publisher rating: rating from borrower to owner
                Rating pubRating = ratingRepository
                        .findByBorrowIdAndFromUserId(completedBorrow.getId(), completedBorrow.getBorrowerId())
                        .orElse(null);
                if (pubRating != null) {
                    publisherRatingScore = (double) pubRating.getScore();
                    publisherRatingStars = scoreToStars(pubRating.getScore());
                }
                // Peer rating: rating from owner to borrower
                Rating peerR = ratingRepository
                        .findByBorrowIdAndFromUserId(completedBorrow.getId(), item.getUserId())
                        .orElse(null);
                if (peerR != null) {
                    peerRatingScore = (double) peerR.getScore();
                    peerRatingStars = scoreToStars(peerR.getScore());
                }
            }
        }

        // Violation info (use violatedBy UUID, not lazy-loaded violator entity)
        String violatorName = getViolatorName(item.getViolatedBy());

        return ContentItemDTO.builder()
                .id(item.getId())
                .type("idle")
                .title(item.getTitle())
                .description(item.getDescription())
                .category(item.getCategory())
                .price(item.getPrice())
                .condition(item.getCondition())
                .publisherName(user != null ? user.getName() : "未知用户")
                .publisherRoom(user != null ? formatRoomWithType(user) : "")
                .displayStatus(displayStatus(item.getStatus()))
                .rawStatus(item.getStatus())
                .isProxy(item.getIsProxy())
                .createdAt(item.getCreatedAt())
                .peerName(peerName)
                .peerRoom(peerRoom)
                .peerRating(peerRating)
                .timeStart(timeStart)
                .timeEnd(timeEnd)
                .publisherRatingStars(publisherRatingStars)
                .publisherRatingScore(publisherRatingScore)
                .peerRatingStars(peerRatingStars)
                .peerRatingScore(peerRatingScore)
                .violationType(item.getViolationType())
                .violationReason(item.getViolationReason())
                .violatorName(violatorName)
                .violatedAt(item.getViolatedAt())
                .building(user != null ? getBuildingName(user) : null)
                .build();
    }

    /**
     * Convert a HelpRequest entity to a ContentItemDTO with full peer/rating/violation info.
     */
    private ContentItemDTO toContentItemDTO(HelpRequest item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);

        // Peer info from related HelpApplication
        String peerName = null;
        String peerRoom = null;
        Double peerRating = null;
        String publisherRatingStars = null;
        Double publisherRatingScore = null;
        String peerRatingStars = null;
        Double peerRatingScore = null;

        if ("helping".equals(item.getStatus())) {
            HelpApplication activeApp = findActiveHelpApplication(item.getId());
            if (activeApp != null) {
                User helper = userRepository.findById(activeApp.getHelperId()).orElse(null);
                if (helper != null) {
                    peerName = helper.getName();
                    peerRoom = formatRoomWithType(helper);
                }
            }
        } else if ("completed".equals(item.getStatus())) {
            HelpApplication completedApp = findCompletedHelpApplication(item.getId());
            if (completedApp != null) {
                User helper = userRepository.findById(completedApp.getHelperId()).orElse(null);
                if (helper != null) {
                    peerName = helper.getName();
                    peerRoom = formatRoomWithType(helper);
                    peerRating = ratingRepository.getAverageScore(helper.getId());
                }
                // Publisher rating: rating from helper to requester
                Rating pubRating = ratingRepository
                        .findByHelpApplicationIdAndFromUserId(completedApp.getId(), completedApp.getHelperId())
                        .orElse(null);
                if (pubRating != null) {
                    publisherRatingScore = (double) pubRating.getScore();
                    publisherRatingStars = scoreToStars(pubRating.getScore());
                }
                // Peer rating: rating from requester to helper
                Rating peerR = ratingRepository
                        .findByHelpApplicationIdAndFromUserId(completedApp.getId(), item.getUserId())
                        .orElse(null);
                if (peerR != null) {
                    peerRatingScore = (double) peerR.getScore();
                    peerRatingStars = scoreToStars(peerR.getScore());
                }
            }
        }

        // Violation info (HelpRequest has no @ManyToOne violator, look up manually)
        String violatorName = getViolatorName(item.getViolatedBy());

        return ContentItemDTO.builder()
                .id(item.getId())
                .type("help")
                .title(item.getTitle())
                .description(item.getDescription())
                .category(item.getCategory())
                .publisherName(user != null ? user.getName() : "未知用户")
                .publisherRoom(user != null ? formatRoomWithType(user) : "")
                .displayStatus(displayStatus(item.getStatus()))
                .rawStatus(item.getStatus())
                .isProxy(item.getIsProxy())
                .isUrgent(item.getIsUrgent())
                .createdAt(item.getCreatedAt())
                .timeStart(item.getTimeStart())
                .timeEnd(item.getTimeEnd())
                .peerName(peerName)
                .peerRoom(peerRoom)
                .peerRating(peerRating)
                .publisherRatingStars(publisherRatingStars)
                .publisherRatingScore(publisherRatingScore)
                .peerRatingStars(peerRatingStars)
                .peerRatingScore(peerRatingScore)
                .violationType(item.getViolationType())
                .violationReason(item.getViolationReason())
                .violatorName(violatorName)
                .violatedAt(item.getViolatedAt())
                .building(user != null ? getBuildingName(user) : null)
                .build();
    }

    // ==================== Private Helpers: Data Fetching ====================

    /**
     * Fetch idle items filtered by statuses, building user IDs, and search keyword.
     */
    private List<IdleItem> fetchIdleItems(List<String> statuses, List<UUID> buildingUserIds, String search) {
        List<IdleItem> allItems = new ArrayList<>();
        for (String status : statuses) {
            allItems.addAll(idleItemRepository.findByStatus(status));
        }

        // Apply building filter
        if (buildingUserIds != null) {
            allItems = allItems.stream()
                    .filter(i -> buildingUserIds.contains(i.getUserId()))
                    .collect(Collectors.toList());
        }

        // Apply search filter
        if (search != null && !search.isEmpty()) {
            String keyword = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(i -> i.getTitle() != null && i.getTitle().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
        }

        return allItems;
    }

    /**
     * Fetch help items filtered by statuses, building user IDs, and search keyword.
     */
    private List<HelpRequest> fetchHelpItems(List<String> statuses, List<UUID> buildingUserIds, String search) {
        List<HelpRequest> allItems = new ArrayList<>();
        for (String status : statuses) {
            allItems.addAll(helpRequestRepository.findByStatus(status));
        }

        // Apply building filter
        if (buildingUserIds != null) {
            allItems = allItems.stream()
                    .filter(h -> buildingUserIds.contains(h.getUserId()))
                    .collect(Collectors.toList());
        }

        // Apply search filter
        if (search != null && !search.isEmpty()) {
            String keyword = search.toLowerCase();
            allItems = allItems.stream()
                    .filter(h -> h.getTitle() != null && h.getTitle().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
        }

        return allItems;
    }

    private BorrowRequest findActiveBorrowRequest(UUID idleId) {
        List<BorrowRequest> borrows = borrowRequestRepository.findByIdleId(idleId);
        return borrows.stream()
                .filter(b -> "active".equals(b.getStatus()) || "approved".equals(b.getStatus()))
                .findFirst().orElse(null);
    }

    private BorrowRequest findCompletedBorrowRequest(UUID idleId) {
        List<BorrowRequest> borrows = borrowRequestRepository.findByIdleId(idleId);
        return borrows.stream()
                .filter(b -> "returned".equals(b.getStatus()))
                .findFirst().orElse(null);
    }

    private HelpApplication findActiveHelpApplication(UUID helpId) {
        List<HelpApplication> apps = helpApplicationRepository.findByHelpId(helpId);
        return apps.stream()
                .filter(a -> "accepted".equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    private HelpApplication findCompletedHelpApplication(UUID helpId) {
        List<HelpApplication> apps = helpApplicationRepository.findByHelpId(helpId);
        return apps.stream()
                .filter(a -> "completed".equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    // ==================== Private Helpers: Building Resolution ====================

    /**
     * Resolve a building name to the list of user IDs living in that building.
     */
    private List<UUID> resolveBuildingUserIds(String buildingName) {
        List<Building> allBuildings = buildingRepository.findAll();
        Building targetBuilding = allBuildings.stream()
                .filter(b -> b.getName() != null && b.getName().contains(buildingName))
                .findFirst().orElse(null);
        if (targetBuilding == null) {
            return new ArrayList<>();
        }

        List<Unit> units = unitRepository.findByBuildingId(targetBuilding.getId());
        List<UUID> roomIds = new ArrayList<>();
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

    // ==================== Private Helpers: Status Mapping ====================

    /**
     * Map a UI status tab to the list of IdleItem DB status values.
     */
    private List<String> mapStatusTabToIdleStatuses(String statusTab) {
        switch (statusTab) {
            case "showing":
                return java.util.Collections.singletonList("online");
            case "progressing":
                return java.util.Collections.singletonList("borrowing");
            case "completed":
                return java.util.Collections.singletonList("completed");
            case "violation":
                return java.util.Collections.singletonList("deleted");
            case "all":
            default:
                return java.util.Arrays.asList("online", "borrowing", "completed", "deleted");
        }
    }

    /**
     * Map a UI status tab to the list of HelpRequest DB status values.
     */
    private List<String> mapStatusTabToHelpStatuses(String statusTab) {
        switch (statusTab) {
            case "showing":
                return java.util.Collections.singletonList("online");
            case "progressing":
                return java.util.Collections.singletonList("helping");
            case "completed":
                return java.util.Collections.singletonList("completed");
            case "violation":
                return java.util.Collections.singletonList("deleted");
            case "all":
            default:
                return java.util.Arrays.asList("online", "helping", "completed", "deleted");
        }
    }

    /**
     * Map a raw DB status to a Chinese display status.
     */
    private String displayStatus(String rawStatus) {
        if (rawStatus == null) return "未知";
        switch (rawStatus) {
            case "online":
                return "展示中";
            case "borrowing":
            case "helping":
                return "进行中";
            case "completed":
                return "已完成";
            case "deleted":
                return "已下架";
            default:
                return rawStatus;
        }
    }

    // ==================== Private Helpers: Formatting ====================

    private String formatRoom(User user) {
        if (user.getRoom() == null) {
            return "";
        }
        try {
            String buildingName = "";
            String unitName = "";
            String roomNumber = "";

            if (user.getRoom().getUnit() != null) {
                unitName = user.getRoom().getUnit().getName() != null
                        ? user.getRoom().getUnit().getName() : "";
                if (user.getRoom().getUnit().getBuilding() != null) {
                    buildingName = user.getRoom().getUnit().getBuilding().getName() != null
                            ? user.getRoom().getUnit().getBuilding().getName() : "";
                }
            }
            roomNumber = user.getRoom().getRoomNumber() != null
                    ? user.getRoom().getRoomNumber() : "";

            return buildingName + unitName + roomNumber + "号";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Format room with user type suffix, e.g. "3栋2单元1502号(业主)".
     */
    private String formatRoomWithType(User user) {
        String baseRoom = formatRoom(user);
        if (baseRoom == null || baseRoom.isEmpty()) {
            return "";
        }
        String userType = getUserTypeLabel(user.getUserType());
        return baseRoom + "(" + userType + ")";
    }

    private String getUserTypeLabel(String userType) {
        if (userType == null) return "业主";
        return switch (userType) {
            case "业主" -> "业主";
            case "租客" -> "租客";
            case "物业" -> "物业";
            case "admin" -> "管理员";
            case "super_admin" -> "超级管理员";
            case "owner" -> "业主";
            case "resident", "tenant" -> "租客";
            default -> userType;
        };
    }

    /**
     * Extract building name from a user's room chain.
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
            // ignore
        }
        return null;
    }

    /**
     * Convert a numeric score (1-5) to star string representation.
     */
    private String scoreToStars(int score) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < score ? "★" : "☆");
        }
        return sb.toString();
    }

    /**
     * Mask a phone number to show only first 3 and last 4 digits.
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Look up violator name by ID (for HelpRequest which has no @ManyToOne violator field).
     */
    private String getViolatorName(UUID violatedBy) {
        if (violatedBy == null) return null;
        return userRepository.findById(violatedBy).map(User::getName).orElse(null);
    }

    // ==================== Private Helpers: Notification ====================

    private void createNotification(UUID userId, String type, String title, String content, UUID relatedId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}
