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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class HelpService {

    private static final Logger log = LoggerFactory.getLogger(HelpService.class);

    private final HelpRequestRepository helpRequestRepository;
    private final HelpApplicationRepository helpApplicationRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RatingRepository ratingRepository;

    public HelpService(HelpRequestRepository helpRequestRepository,
                       HelpApplicationRepository helpApplicationRepository,
                       NotificationRepository notificationRepository,
                       UserRepository userRepository,
                       RoomRepository roomRepository,
                       RatingRepository ratingRepository) {
        this.helpRequestRepository = helpRequestRepository;
        this.helpApplicationRepository = helpApplicationRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.ratingRepository = ratingRepository;
    }

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public HelpResponseDTO publish(Long userId, HelpRequestDTO req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        HelpRequest helpRequest = new HelpRequest();
        helpRequest.setUserId(userId);
        helpRequest.setTenantId(user.getTenantId());
        helpRequest.setTitle(req.getTitle());
        helpRequest.setDescription(req.getDescription());
        helpRequest.setCategory(req.getCategory());
        helpRequest.setIsUrgent(req.getIsUrgent() != null && req.getIsUrgent());
        if (req.getTimeStart() != null && !req.getTimeStart().isEmpty()) {
            try {
                helpRequest.setTimeStart(LocalDateTime.parse(req.getTimeStart(), DT_FMT));
            } catch (Exception e) {
                log.debug("Failed to parse timeStart: {}", e.getMessage());
            }
        }
        if (req.getTimeEnd() != null && !req.getTimeEnd().isEmpty()) {
            try {
                helpRequest.setTimeEnd(LocalDateTime.parse(req.getTimeEnd(), DT_FMT));
            } catch (Exception e) {
                log.debug("Failed to parse timeEnd: {}", e.getMessage());
            }
        }
        helpRequest.setImages(req.getImages());
        helpRequest.setStatus("online");
        helpRequest.setIsProxy(false);
        helpRequest.setCreatedAt(LocalDateTime.now());
        helpRequest = helpRequestRepository.save(helpRequest);

        return toDTO(helpRequest);
    }

    public PageDTO<HelpResponseDTO> getHomeList(Long userId, int page, int size) {
        User user = userRepository.findById(userId).orElse(null);
        Long tenantId = user != null ? user.getTenantId() : null;
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isUrgent", "createdAt"));
        Page<HelpRequest> helpPage = tenantId != null
                ? helpRequestRepository.findByStatusAndTenantId("online", tenantId, pageRequest)
                : helpRequestRepository.findByStatus("online", pageRequest);

        List<HelpResponseDTO> dtos = helpPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageDTO.<HelpResponseDTO>builder()
                .content(dtos)
                .totalElements(helpPage.getTotalElements())
                .totalPages(helpPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    public HelpResponseDTO getDetail(Long helpId) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));
        return enrichWithUserStats(toDTO(helpRequest));
    }

    public PageDTO<HelpResponseDTO> search(Long userId, String keyword, int page, int size) {
        // 与 getHomeList 保持一致的租户隔离——不同小区的数据不得互相搜到
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Long tenantId = user != null ? user.getTenantId() : null;
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isUrgent", "createdAt"));
        Page<HelpRequest> helpPage = tenantId != null
                ? helpRequestRepository.searchByTenant("online", tenantId, keyword, keyword, pageRequest)
                : helpRequestRepository.findByStatusAndTitleContainingOrDescriptionContaining(
                        "online", keyword, keyword, pageRequest);

        List<HelpResponseDTO> dtos = helpPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageDTO.<HelpResponseDTO>builder()
                .content(dtos)
                .totalElements(helpPage.getTotalElements())
                .totalPages(helpPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    public List<HelpResponseDTO> getMyPosts(Long userId) {
        List<HelpRequest> requests = helpRequestRepository.findByUserId(userId);
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public HelpResponseDTO delist(Long userId, Long helpId) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该求助");
        }

        helpRequest.setStatus("offline");
        helpRequest = helpRequestRepository.save(helpRequest);
        return toDTO(helpRequest);
    }

    public HelpResponseDTO apply(Long helperId, Long helpId, String note) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!"online".equals(helpRequest.getStatus())) {
            throw new RuntimeException("该求助已关闭");
        }

        if (helpRequest.getUserId().equals(helperId)) {
            throw new RuntimeException("不能申请自己的求助");
        }

        // 防重复：同一用户对同一求助已有 pending/approved 申请时拒绝
        if (helpApplicationRepository.existsByHelpIdAndHelperIdAndStatusIn(
                helpId, helperId, List.of("pending", "approved"))) {
            throw new RuntimeException("您已申请过该求助，请勿重复提交");
        }

        HelpApplication application = new HelpApplication();
        application.setHelpId(helpId);
        application.setHelperId(helperId);
        application.setNote(note);
        application.setStatus("pending");
        application.setCreatedAt(LocalDateTime.now());
        application = helpApplicationRepository.save(application);

        createNotification(helpRequest.getUserId(), "help_application",
                "新的帮助申请", "有人想帮助您：[" + helpRequest.getTitle() + "]",
                application.getId());

        return toDTO(helpRequest);
    }

    public HelpResponseDTO approveReject(Long ownerId, Long appId, ApproveRequest req) {
        HelpApplication application = helpApplicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("帮助申请不存在"));

        HelpRequest helpRequest = helpRequestRepository.findById(application.getHelpId())
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(ownerId)) {
            throw new RuntimeException("无权操作该申请");
        }

        if (!"pending".equals(application.getStatus())) {
            throw new RuntimeException("该申请已被处理，无法重复操作");
        }

        application.setStatus(req.getApproved() ? "approved" : "rejected");
        helpApplicationRepository.save(application);

        // 同步 HelpRequest 状态
        if (req.getApproved()) {
            helpRequest.setStatus("helping");
            helpRequestRepository.save(helpRequest);
        }

        String title = req.getApproved() ? "帮助申请已通过" : "帮助申请被拒绝";
        String content = req.getApproved()
                ? "您对求助「" + helpRequest.getTitle() + "」的帮助申请已通过"
                : "您对求助「" + helpRequest.getTitle() + "」的帮助申请被拒绝"
                        + (req.getReason() != null ? "，原因：" + req.getReason() : "");
        createNotification(application.getHelperId(), "help_result", title, content, application.getId());

        return toDTO(helpRequest);
    }

    /**
     * 完成帮助申请 — 由求助方调用。
     */
    /**
     * 确认结束互助 —— 求助方(helpReq) / 帮助方(helpPro) 任意一方均可发起。
     * 二者是同一条 HelpApplication 的两个视角，状态共享，
     * 任意一方确认后双方都会从「进行中」进入「已完成」。
     */
    public HelpResponseDTO completeHelp(Long actorId, Long appId) {
        HelpApplication application = helpApplicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("帮助申请不存在"));

        HelpRequest helpRequest = helpRequestRepository.findById(application.getHelpId())
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        boolean isRequester = helpRequest.getUserId().equals(actorId);
        boolean isHelper = application.getHelperId().equals(actorId);
        if (!isRequester && !isHelper) {
            throw new RuntimeException("无权操作该申请");
        }

        if (!"approved".equals(application.getStatus())) {
            throw new RuntimeException("只能完成进行中的帮助申请");
        }

        application.setStatus("completed");
        application.setCompletedAt(LocalDateTime.now());
        helpApplicationRepository.save(application);

        helpRequest.setStatus("completed");
        helpRequestRepository.save(helpRequest);

        // 通知对方（发起人是求助方则通知帮助方，反之亦然）
        Long peerId = isRequester ? application.getHelperId() : helpRequest.getUserId();
        if (peerId != null) {
            createNotification(peerId, "help_result",
                    "帮助已完成", "「" + helpRequest.getTitle() + "」的互助已确认完成",
                    application.getId());
        }

        return toDTO(helpRequest);
    }

    /**
     * 更新求助信息（编辑保存或重新上架）。
     * 若当前状态为 completed/offline，自动重新上架为 online。
     */
    public HelpResponseDTO update(Long userId, Long helpId, HelpRequestDTO req) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该求助");
        }

        helpRequest.setTitle(req.getTitle() != null ? req.getTitle() : helpRequest.getTitle());
        helpRequest.setDescription(req.getDescription() != null ? req.getDescription() : helpRequest.getDescription());
        helpRequest.setCategory(req.getCategory() != null ? req.getCategory() : helpRequest.getCategory());
        helpRequest.setIsUrgent(req.getIsUrgent() != null ? req.getIsUrgent() : helpRequest.getIsUrgent());

        if (req.getTimeStart() != null && !req.getTimeStart().isEmpty()) {
            try {
                helpRequest.setTimeStart(LocalDateTime.parse(req.getTimeStart(), DT_FMT));
            } catch (Exception e) { log.debug("Failed to parse timeStart: {}", e.getMessage()); }
        }
        if (req.getTimeEnd() != null && !req.getTimeEnd().isEmpty()) {
            try {
                helpRequest.setTimeEnd(LocalDateTime.parse(req.getTimeEnd(), DT_FMT));
            } catch (Exception e) { log.debug("Failed to parse timeEnd: {}", e.getMessage()); }
        }
        helpRequest.setImages(req.getImages());

        // 自动重新上架：completed/offline → online
        if ("completed".equals(helpRequest.getStatus()) || "offline".equals(helpRequest.getStatus())) {
            helpRequest.setStatus("online");
        }

        helpRequest = helpRequestRepository.save(helpRequest);
        return toDTO(helpRequest);
    }

    public List<HelpResponseDTO> getMyApplications(Long userId) {
        List<HelpApplication> applications = helpApplicationRepository.findByHelperId(userId);
        return applications.stream().map(app -> {
            HelpRequest helpRequest = helpRequestRepository.findById(app.getHelpId())
                    .orElse(null);
            HelpResponseDTO dto = helpRequest != null ? toDTO(helpRequest) : HelpResponseDTO.builder().build();
            dto.setApplicationStatus(app.getStatus());
            dto.setApplicationId(app.getId());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<HelpResponseDTO> getPendingApprovals(Long userId) {
        List<HelpRequest> myRequests = helpRequestRepository.findByUserId(userId);
        List<HelpResponseDTO> result = new ArrayList<>();
        for (HelpRequest hr : myRequests) {
            List<HelpApplication> pendingApps = helpApplicationRepository
                    .findByHelpIdAndStatus(hr.getId(), "pending");
            for (HelpApplication app : pendingApps) {
                User helper = userRepository.findById(app.getHelperId()).orElse(null);
                HelpResponseDTO dto = toDTO(hr);
                dto.setApplicationId(app.getId());
                dto.setApplicationStatus(app.getStatus());
                dto.setHelperId(app.getHelperId());
                dto.setHelperName(helper != null ? helper.getName() : "未知用户");
                dto.setApplicationNote(app.getNote());
                result.add(dto);
            }
        }
        return result;
    }

    private HelpResponseDTO enrichWithUserStats(HelpResponseDTO dto) {
        Long userId = dto.getUserId();
        if (userId == null) return dto;

        Double avgScore = ratingRepository.getAverageScore(userId);
        dto.setRating(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null);

        long helpCount = helpRequestRepository.findByUserId(userId).size();
        dto.setHelpCount(helpCount);

        long helpedCount = helpApplicationRepository.countByHelperIdAndStatus(userId, "approved");
        dto.setHelpedCount(helpedCount);
        return dto;
    }

    private HelpResponseDTO toDTO(HelpRequest hr) {
        User user = userRepository.findById(hr.getUserId()).orElse(null);
        String userRoom = formatRoom(user);
        return HelpResponseDTO.builder()
                .id(hr.getId())
                .userId(hr.getUserId())
                .userName(user != null ? user.getName() : "未知用户")
                .userRoom(userRoom)
                .title(hr.getTitle())
                .description(hr.getDescription())
                .category(hr.getCategory())
                .isUrgent(hr.getIsUrgent())
                .timeStart(hr.getTimeStart())
                .timeEnd(hr.getTimeEnd())
                .images(hr.getImages())
                .status(hr.getStatus())
                .delistReason(hr.getDelistReason())
                .isProxy(hr.getIsProxy())
                .createdAt(hr.getCreatedAt())
                .build();
    }

    private String formatRoom(User user) {
        if (user == null || user.getRoom() == null) {
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

            String typeLabel = getUserTypeLabel(user.getUserType());
            String addr = buildingName + unitName + roomNumber + "号";
            return typeLabel.isEmpty() ? addr : addr + "(" + typeLabel + ")";
        } catch (Exception e) {
            return "";
        }
    }

    private String getUserTypeLabel(String userType) {
        if (userType == null) return "";
        switch (userType) {
            case "owner": return "业主";
            case "tenant": return "租客";
            case "admin": return "管理员";
            case "super_admin": return "超级管理员";
            default: return userType != null ? userType : "";
        }
    }

    private void createNotification(Long userId, String type, String title, String content, Long relatedId) {
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
