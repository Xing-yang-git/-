package com.platform.service;

import com.platform.model.dto.ApproveRequest;
import com.platform.model.dto.BorrowRequestDTO;
import com.platform.model.dto.BorrowResponseDTO;
import com.platform.model.dto.ReturnRequest;
import com.platform.model.entity.BorrowRequest;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.Notification;
import com.platform.model.entity.User;
import com.platform.repository.BorrowRequestRepository;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.NotificationRepository;
import com.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class BorrowService {

    private final BorrowRequestRepository borrowRequestRepository;
    private final IdleItemRepository idleItemRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BorrowService(BorrowRequestRepository borrowRequestRepository,
                         IdleItemRepository idleItemRepository,
                         NotificationRepository notificationRepository,
                         UserRepository userRepository) {
        this.borrowRequestRepository = borrowRequestRepository;
        this.idleItemRepository = idleItemRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    public BorrowResponseDTO getDetail(UUID borrowId) {
        BorrowRequest br = borrowRequestRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("借入记录不存在"));
        return toDTO(br);
    }

    public BorrowResponseDTO apply(UUID borrowerId, BorrowRequestDTO req) {
        IdleItem idleItem = idleItemRepository.findById(req.getIdleId())
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!"online".equals(idleItem.getStatus())) {
            throw new RuntimeException("该物品已下架");
        }

        if (idleItem.getUserId().equals(borrowerId)) {
            throw new RuntimeException("不能借入自己的物品");
        }

        BorrowRequest borrowRequest = new BorrowRequest();
        borrowRequest.setIdleId(req.getIdleId());
        borrowRequest.setBorrowerId(borrowerId);
        borrowRequest.setDurationType(req.getDurationType() != null ? req.getDurationType() : "day");
        borrowRequest.setDurationDays(req.getDurationDays() != null ? req.getDurationDays() : 7);
        if (req.getStartDate() != null && !req.getStartDate().isEmpty()) {
            borrowRequest.setStartDate(LocalDate.parse(req.getStartDate()));
        }
        borrowRequest.setNote(req.getNote());
        borrowRequest.setStatus("pending");
        borrowRequest.setCreatedAt(LocalDateTime.now());
        borrowRequest = borrowRequestRepository.save(borrowRequest);

        createNotification(idleItem.getUserId(), "borrow_request",
                "新的借入申请", "有人想借入您的物品：" + idleItem.getTitle(),
                borrowRequest.getId());

        return toDTO(borrowRequest);
    }

    public BorrowResponseDTO approveReject(UUID ownerId, UUID borrowId, ApproveRequest req) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("借入申请不存在"));

        IdleItem idleItem = idleItemRepository.findById(borrowRequest.getIdleId())
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!idleItem.getUserId().equals(ownerId)) {
            throw new RuntimeException("无权操作该申请");
        }

        if (!"pending".equals(borrowRequest.getStatus())) {
            throw new RuntimeException("该申请已被处理，无法重复操作");
        }

        borrowRequest.setStatus(req.getApproved() ? "approved" : "rejected");
        borrowRequest = borrowRequestRepository.save(borrowRequest);

        // Sync IdleItem status
        if (req.getApproved()) {
            idleItem.setStatus("borrowing");
            idleItemRepository.save(idleItem);
        }

        String title = req.getApproved() ? "借入申请已通过" : "借入申请被拒绝";
        String content = req.getApproved()
                ? "您对物品「" + idleItem.getTitle() + "」的借入申请已通过"
                : "您对物品「" + idleItem.getTitle() + "」的借入申请被拒绝"
                        + (req.getReason() != null ? "，原因：" + req.getReason() : "");
        createNotification(borrowRequest.getBorrowerId(), "borrow_result", title, content, borrowRequest.getId());

        return toDTO(borrowRequest);
    }

    public List<BorrowResponseDTO> getMyApplications(UUID userId) {
        List<BorrowRequest> requests = borrowRequestRepository.findByBorrowerId(userId);
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BorrowResponseDTO> getPendingApprovals(UUID userId) {
        List<IdleItem> myItems = idleItemRepository.findByUserId(userId);
        List<UUID> myItemIds = myItems.stream().map(IdleItem::getId).collect(Collectors.toList());
        if (myItemIds.isEmpty()) return new ArrayList<>();
        List<BorrowRequest> pendingRequests = borrowRequestRepository
                .findByIdleIdInAndStatus(myItemIds, "pending");
        return pendingRequests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public BorrowResponseDTO confirmReturn(UUID borrowerId, UUID borrowId, ReturnRequest req) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("借入记录不存在"));

        if (!borrowRequest.getBorrowerId().equals(borrowerId)) {
            throw new RuntimeException("无权操作该记录");
        }

        borrowRequest.setReturnStatus(req.getReturnStatus());
        borrowRequest.setReturnNote(req.getReturnNote());
        borrowRequest.setDamageType(req.getDamageType());
        borrowRequest.setDamageNote(req.getDamageNote());
        borrowRequest.setIsOnTime(req.getIsOnTime());
        borrowRequest.setReturnPhotos(req.getReturnPhotos());
        borrowRequest.setStatus("returned");
        borrowRequest = borrowRequestRepository.save(borrowRequest);

        IdleItem idleItem = idleItemRepository.findById(borrowRequest.getIdleId()).orElse(null);
        if (idleItem != null) {
            idleItem.setStatus("completed");
            idleItemRepository.save(idleItem);
            UUID ownerId = idleItem.getUserId();
            if (ownerId != null) {
                createNotification(ownerId, "return_confirm",
                        "物品已归还", "借出的物品已被归还",
                        borrowRequest.getId());
            }
        }

        return toDTO(borrowRequest);
    }

    private BorrowResponseDTO toDTO(BorrowRequest br) {
        IdleItem idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
        UUID ownerId = idleItem != null ? idleItem.getUserId() : null;
        User owner = ownerId != null ? userRepository.findById(ownerId).orElse(null) : null;
        User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);

        return BorrowResponseDTO.builder()
                .id(br.getId())
                .idleId(br.getIdleId())
                .idleTitle(idleItem != null ? idleItem.getTitle() : "未知物品")
                .itemImage(extractFirstImage(idleItem))
                .ownerId(ownerId)
                .ownerName(owner != null ? owner.getName() : "未知用户")
                .borrowerId(br.getBorrowerId())
                .borrowerName(borrower != null ? borrower.getName() : "未知用户")
                .durationType(br.getDurationType())
                .durationDays(br.getDurationDays())
                .startDate(br.getStartDate())
                .note(br.getNote())
                .status(br.getStatus())
                .returnStatus(br.getReturnStatus())
                .damageType(br.getDamageType())
                .isOnTime(br.getIsOnTime())
                .returnPhotos(br.getReturnPhotos())
                .createdAt(br.getCreatedAt())
                .build();
    }

    private String extractFirstImage(IdleItem idleItem) {
        if (idleItem == null || idleItem.getImages() == null || idleItem.getImages().isEmpty()) {
            return "";
        }
        try {
            List<String> urls = objectMapper.readValue(idleItem.getImages(), new TypeReference<List<String>>() {});
            return urls.isEmpty() ? "" : urls.get(0);
        } catch (Exception e) {
            return "";
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
