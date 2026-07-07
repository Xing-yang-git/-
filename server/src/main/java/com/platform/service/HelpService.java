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
import java.util.UUID;
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

    public HelpResponseDTO publish(UUID userId, HelpRequestDTO req) {
        HelpRequest helpRequest = new HelpRequest();
        helpRequest.setUserId(userId);
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
        helpRequest.setCreatedAt(LocalDateTime.now());
        helpRequest = helpRequestRepository.save(helpRequest);

        return toDTO(helpRequest);
    }

    public PageDTO<HelpResponseDTO> getHomeList(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isUrgent", "createdAt"));
        Page<HelpRequest> helpPage = helpRequestRepository.findByStatus("online", pageRequest);

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

    public HelpResponseDTO getDetail(UUID helpId) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));
        return enrichWithUserStats(toDTO(helpRequest));
    }

    public PageDTO<HelpResponseDTO> search(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "isUrgent", "createdAt"));
        Page<HelpRequest> helpPage = helpRequestRepository
                .findByStatusAndTitleContainingOrDescriptionContaining(
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

    public List<HelpResponseDTO> getMyPosts(UUID userId) {
        List<HelpRequest> requests = helpRequestRepository.findByUserId(userId);
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public HelpResponseDTO delist(UUID userId, UUID helpId) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该求助");
        }

        helpRequest.setStatus("offline");
        helpRequest = helpRequestRepository.save(helpRequest);
        return toDTO(helpRequest);
    }

    public HelpResponseDTO apply(UUID helperId, UUID helpId, String note) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!"online".equals(helpRequest.getStatus())) {
            throw new RuntimeException("该求助已关闭");
        }

        if (helpRequest.getUserId().equals(helperId)) {
            throw new RuntimeException("不能申请自己的求助");
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

    public HelpResponseDTO approveReject(UUID ownerId, UUID appId, ApproveRequest req) {
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

        // Sync HelpRequest status
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
     * Complete a help application — called by the help requester.
     */
    public HelpResponseDTO completeHelp(UUID ownerId, UUID appId) {
        HelpApplication application = helpApplicationRepository.findById(appId)
                .orElseThrow(() -> new RuntimeException("帮助申请不存在"));

        HelpRequest helpRequest = helpRequestRepository.findById(application.getHelpId())
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(ownerId)) {
            throw new RuntimeException("无权操作该申请");
        }

        if (!"approved".equals(application.getStatus())) {
            throw new RuntimeException("只能完成已批准的帮助申请");
        }

        application.setStatus("completed");
        application.setCompletedAt(LocalDateTime.now());
        helpApplicationRepository.save(application);

        helpRequest.setStatus("completed");
        helpRequestRepository.save(helpRequest);

        createNotification(application.getHelperId(), "help_result",
                "帮助已完成", "您对「" + helpRequest.getTitle() + "」的帮助已确认完成",
                application.getId());

        return toDTO(helpRequest);
    }

    /**
     * Update a help request (edit save or relist).
     * If current status is completed/offline, auto-relists to online.
     */
    public HelpResponseDTO update(UUID userId, UUID helpId, HelpRequestDTO req) {
        HelpRequest helpRequest = helpRequestRepository.findById(helpId)
                .orElseThrow(() -> new RuntimeException("求助信息不存在"));

        if (!helpRequest.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该求助");
        }

        helpRequest.setTitle(req.getTitle() != null ? req.getTitle() : helpRequest.getTitle());
        helpRequest.setDescription(req.getDescription() != null ? req.getDescription() : helpRequest.getDescription());
        helpRequest.setCategory(req.getCategory() != null ? req.getCategory() : helpRequest.getCategory());
        helpRequest.setIsUrgent(req.getIsUrgent() != null ? req.getIsUrgent() : helpRequest.getIsUrgent());
        helpRequest.setLocation(req.getLocation() != null ? req.getLocation() : helpRequest.getLocation());
        helpRequest.setRewardType(req.getRewardType() != null ? req.getRewardType() : helpRequest.getRewardType());

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

        // Auto-relist: completed/offline → online
        if ("completed".equals(helpRequest.getStatus()) || "offline".equals(helpRequest.getStatus())) {
            helpRequest.setStatus("online");
        }

        helpRequest = helpRequestRepository.save(helpRequest);
        return toDTO(helpRequest);
    }

    public List<HelpResponseDTO> getMyApplications(UUID userId) {
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

    public List<HelpResponseDTO> getPendingApprovals(UUID userId) {
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
        UUID userId = dto.getUserId();
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
                .location(hr.getLocation())
                .rewardType(hr.getRewardType())
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
            return buildingName + unitName + roomNumber + "号(" + typeLabel + ")";
        } catch (Exception e) {
            return "";
        }
    }

    private String getUserTypeLabel(String userType) {
        if (userType == null) return "";
        switch (userType) {
            case "业主": return "业主";
            case "租客": return "租客";
            case "物业": return "物业";
            case "admin": return "管理员";
            case "super_admin": return "超级管理员";
            default: return userType;
        }
    }

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
