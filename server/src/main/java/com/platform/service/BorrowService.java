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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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

    public BorrowResponseDTO getDetail(Long borrowId) {
        BorrowRequest br = borrowRequestRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("借入记录不存在"));
        return toDTO(br);
    }

    public BorrowResponseDTO apply(Long borrowerId, BorrowRequestDTO req) {
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
        borrowRequest.setNote(req.getNote());
        borrowRequest.setStatus("pending");
        borrowRequest.setCreatedAt(LocalDateTime.now());
        borrowRequest = borrowRequestRepository.save(borrowRequest);

        boolean wanted = "WANTED".equals(idleItem.getPostType());
        createNotification(idleItem.getUserId(), "borrow_request",
                wanted ? "新的借出意向" : "新的借入申请",
                wanted ? ("有人愿意借出给您：" + idleItem.getTitle())
                        : ("有人想借入您的物品：" + idleItem.getTitle()),
                borrowRequest.getId());

        return toDTO(borrowRequest);
    }

    public BorrowResponseDTO approveReject(Long ownerId, Long borrowId, ApproveRequest req) {
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

        // 同步 IdleItem 状态
        if (req.getApproved()) {
            idleItem.setStatus("borrowing");
            idleItemRepository.save(idleItem);
        }

        // WANTED(需求借入) 帖：对方(borrowerId) 其实是出借方，通知文案按"借出意向"表述
        boolean wanted = "WANTED".equals(idleItem.getPostType());
        String title = req.getApproved()
                ? (wanted ? "借出意向已被确认" : "借入申请已通过")
                : (wanted ? "借出意向被拒绝" : "借入申请被拒绝");
        String content;
        if (req.getApproved()) {
            content = wanted
                    ? "您对「" + idleItem.getTitle() + "」的借出意向已被确认"
                    : "您对物品「" + idleItem.getTitle() + "」的借入申请已通过";
        } else {
            content = (wanted
                    ? "您对「" + idleItem.getTitle() + "」的借出意向被拒绝"
                    : "您对物品「" + idleItem.getTitle() + "」的借入申请被拒绝")
                    + (req.getReason() != null ? "，原因：" + req.getReason() : "");
        }
        createNotification(borrowRequest.getBorrowerId(), "borrow_result", title, content, borrowRequest.getId());

        return toDTO(borrowRequest);
    }

    public List<BorrowResponseDTO> getMyApplications(Long userId) {
        List<BorrowRequest> requests = borrowRequestRepository.findByBorrowerId(userId);
        return requests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BorrowResponseDTO> getPendingApprovals(Long userId) {
        List<IdleItem> myItems = idleItemRepository.findByUserId(userId);
        List<Long> myItemIds = myItems.stream().map(IdleItem::getId).collect(Collectors.toList());
        if (myItemIds.isEmpty()) return new ArrayList<>();
        List<BorrowRequest> pendingRequests = borrowRequestRepository
                .findByIdleIdInAndStatus(myItemIds, "pending");
        return pendingRequests.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 确认归还 —— 借出双方（借入方 / 物品所有者）任意一方均可发起。
     * 借入(borrow)与借出(lend)是同一条 BorrowRequest 的两个视角，状态共享，
     * 因此任意一方确认归还后，双方的该笔记录都会从「进行中」进入「已完成」。
     */
    public BorrowResponseDTO confirmReturn(Long actorId, Long borrowId, ReturnRequest req) {
        BorrowRequest borrowRequest = borrowRequestRepository.findById(borrowId)
                .orElseThrow(() -> new RuntimeException("借入记录不存在"));

        IdleItem idleItem = idleItemRepository.findById(borrowRequest.getIdleId()).orElse(null);
        Long ownerId = idleItem != null ? idleItem.getUserId() : null;

        boolean isBorrower = borrowRequest.getBorrowerId().equals(actorId);
        boolean isOwner = ownerId != null && ownerId.equals(actorId);
        if (!isBorrower && !isOwner) {
            throw new RuntimeException("无权操作该记录");
        }

        if (!"approved".equals(borrowRequest.getStatus())) {
            throw new RuntimeException("该借入不在进行中，无法归还");
        }

        borrowRequest.setReturnStatus(req.getReturnStatus());
        borrowRequest.setReturnNote(req.getReturnNote());
        borrowRequest.setDamageType(req.getDamageType());
        borrowRequest.setDamageNote(req.getDamageNote());
        borrowRequest.setIsOnTime(req.getIsOnTime());
        borrowRequest.setReturnPhotos(req.getReturnPhotos());
        borrowRequest.setStatus("returned");
        borrowRequest = borrowRequestRepository.save(borrowRequest);

        if (idleItem != null) {
            idleItem.setStatus("completed");
            idleItemRepository.save(idleItem);
        }

        // 通知对方（发起人是借入方则通知所有者，反之亦然）
        Long peerId = isBorrower ? ownerId : borrowRequest.getBorrowerId();
        if (peerId != null) {
            String itemTitle = idleItem != null ? idleItem.getTitle() : "物品";
            createNotification(peerId, "return_confirm",
                    "物品已归还", "「" + itemTitle + "」的借用已归还，交易完成",
                    borrowRequest.getId());
        }

        return toDTO(borrowRequest);
    }

    private BorrowResponseDTO toDTO(BorrowRequest br) {
        IdleItem idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
        Long ownerId = idleItem != null ? idleItem.getUserId() : null;
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
