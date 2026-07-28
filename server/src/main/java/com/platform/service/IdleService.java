package com.platform.service;

import com.platform.common.BizStatus;
import com.platform.common.UserFormatter;
import com.platform.model.dto.IdleItemDTO;
import com.platform.model.dto.IdleItemRequest;
import com.platform.model.dto.PageDTO;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.User;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.BorrowRequestRepository;
import com.platform.repository.RatingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class IdleService {

    private final IdleItemRepository idleItemRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final RatingRepository ratingRepository;
    private final UserActivityService userActivityService;

    public IdleService(IdleItemRepository idleItemRepository,
                       UserRepository userRepository,
                       RoomRepository roomRepository,
                       BorrowRequestRepository borrowRequestRepository,
                       RatingRepository ratingRepository,
                       UserActivityService userActivityService) {
        this.idleItemRepository = idleItemRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.ratingRepository = ratingRepository;
        this.userActivityService = userActivityService;
    }

    public IdleItemDTO publish(Long userId, IdleItemRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        IdleItem item = new IdleItem();
        item.setUserId(userId);
        item.setTenantId(user.getTenantId());
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setPostType(req.getPostType());
        item.setCategory(req.getCategory());
        item.setCondition(req.getCondition() != null ? req.getCondition() : BizStatus.NORMAL);
        item.setImages(req.getImages());
        item.setPrice(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO);
        item.setMaxDuration(req.getMaxDuration() != null ? req.getMaxDuration() : 7);
        item.setDurationUnit(req.getDurationUnit() != null ? req.getDurationUnit() : "day");
        item.setPickupMethod(req.getPickupMethod() != null ? req.getPickupMethod() : "self_pickup");
        item.setIsProxy(req.getIsProxy() != null && req.getIsProxy());
        item.setStatus(BizStatus.ONLINE);
        item.setCreatedAt(LocalDateTime.now());
        item = idleItemRepository.save(item);

        return toDTO(item);
    }

    public PageDTO<IdleItemDTO> getHomeList(String postType, Long userId, int page, int size) {
        User user = userRepository.findById(userId).orElse(null);
        Long tenantId = user != null ? user.getTenantId() : null;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<IdleItem> itemPage = tenantId != null
                ? idleItemRepository.findByStatusAndPostTypeAndTenantId(BizStatus.ONLINE, postType, tenantId, pageRequest)
                : idleItemRepository.findByStatusAndPostType(BizStatus.ONLINE, postType, pageRequest);

        List<IdleItemDTO> dtos = itemPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageDTO.<IdleItemDTO>builder()
                .content(dtos)
                .totalElements(itemPage.getTotalElements())
                .totalPages(itemPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    public IdleItemDTO getDetail(Long itemId) {
        return getDetail(itemId, null);
    }

    /**
     * 获取物品详情，可选带上当前用户的借用申请状态。
     */
    public IdleItemDTO getDetail(Long itemId, Long currentUserId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));
        IdleItemDTO dto = enrichWithUserStats(toDTO(item));

        // 检查当前用户是否对该物品提交过借用申请
        if (currentUserId != null) {
            List<com.platform.model.entity.BorrowRequest> userRequests =
                    borrowRequestRepository.findByIdleId(itemId);
            for (com.platform.model.entity.BorrowRequest br : userRequests) {
                if (currentUserId.equals(br.getBorrowerId())) {
                    dto.setUserBorrowStatus(br.getStatus()); // "pending" | "approved" | "rejected" | "returned"
                    break;
                }
            }
        }

        return dto;
    }

    public PageDTO<IdleItemDTO> search(Long userId, String keyword, String postType, int page, int size) {
        // 与 getHomeList 保持一致的租户隔离——不同小区的数据不得互相搜到
        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Long tenantId = user != null ? user.getTenantId() : null;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<IdleItem> itemPage = tenantId != null
                ? idleItemRepository.searchByTenant(BizStatus.ONLINE, postType, tenantId, keyword, keyword, pageRequest)
                : idleItemRepository.findByStatusAndPostTypeAndTitleContainingOrDescriptionContaining(
                        BizStatus.ONLINE, postType, keyword, keyword, pageRequest);

        List<IdleItemDTO> dtos = itemPage.getContent().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return PageDTO.<IdleItemDTO>builder()
                .content(dtos)
                .totalElements(itemPage.getTotalElements())
                .totalPages(itemPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    public List<IdleItemDTO> getMyPosts(Long userId, String postType) {
        List<IdleItem> items = idleItemRepository
                .findByUserIdAndPostType(userId, postType, Pageable.unpaged())
                .getContent();
        return items.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public IdleItemDTO delist(Long userId, Long itemId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该物品");
        }

        item.setStatus(BizStatus.OFFLINE);
        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    public IdleItemDTO deleteItem(Long userId, Long itemId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该物品");
        }

        item.setStatus(BizStatus.DELETED);
        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    /**
     * 更新闲置物品（编辑保存或重新上架）。
     * 若当前状态为 completed/offline，自动重新上架为 online。
     */
    public IdleItemDTO update(Long userId, Long itemId, IdleItemRequest req) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该物品");
        }

        item.setTitle(req.getTitle() != null ? req.getTitle() : item.getTitle());
        item.setDescription(req.getDescription() != null ? req.getDescription() : item.getDescription());
        item.setCategory(req.getCategory() != null ? req.getCategory() : item.getCategory());
        item.setCondition(req.getCondition() != null ? req.getCondition() : item.getCondition());
        item.setImages(req.getImages() != null ? req.getImages() : item.getImages());
        item.setPrice(req.getPrice() != null ? req.getPrice() : item.getPrice());
        item.setMaxDuration(req.getMaxDuration() != null ? req.getMaxDuration() : item.getMaxDuration());
        item.setDurationUnit(req.getDurationUnit() != null ? req.getDurationUnit() : item.getDurationUnit());
        item.setPickupMethod(req.getPickupMethod() != null ? req.getPickupMethod() : item.getPickupMethod());

        // 自动重新上架：completed/offline → online，并刷新发布时间
        if (BizStatus.COMPLETED.equals(item.getStatus()) || BizStatus.OFFLINE.equals(item.getStatus())) {
            item.setStatus(BizStatus.ONLINE);
            item.setCreatedAt(LocalDateTime.now());
        }

        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    private IdleItemDTO enrichWithUserStats(IdleItemDTO dto) {
        Long userId = dto.getUserId();
        if (userId == null) return dto;

        Double avgScore = ratingRepository.getAverageScore(userId);
        // 暂无评分时默认 5.0（无互助记录的新用户）
        dto.setRating(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 5.0);

        // 「以往记录」弹层五项统计 — 全站统一口径：已完成且被对方评价才计数。
        // 借入/借出按真实角色分流（WANTED 帖 owner/borrower 反转），归还率不设评价门槛。
        UserActivityService.InteractionStats stats = userActivityService.interactionStats(userId);
        dto.setBorrowCount((long) stats.borrowCount());
        dto.setLendCount((long) stats.lendCount());
        dto.setHelpCount((long) stats.helpReqCount());
        dto.setHelpedCount((long) stats.helpProCount());
        dto.setReturnRate(stats.returnedCount() > 0
                ? Math.round(stats.onTimeCount() * 100.0 / stats.returnedCount()) + "%"
                : "100%");
        return dto;
    }

    private IdleItemDTO toDTO(IdleItem item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);
        String userName = user != null ? user.getName() : "未知用户";
        String roomInfo = UserFormatter.formatRoomWithType(user);

        return IdleItemDTO.builder()
                .id(item.getId())
                .userId(item.getUserId())
                .userName(userName)
                .userRoom(roomInfo)
                .title(item.getTitle())
                .description(item.getDescription())
                .postType(item.getPostType())
                .category(item.getCategory())
                .images(item.getImages())
                .price(item.getPrice())
                .condition(item.getCondition())
                .maxDuration(item.getMaxDuration() != null ? item.getMaxDuration() : 7)
                .durationUnit(item.getDurationUnit() != null ? item.getDurationUnit() : "day")
                .pickupMethod(item.getPickupMethod())
                .status(item.getStatus())
                .delistReason(item.getDelistReason())
                .isProxy(item.getIsProxy())
                .createdAt(item.getCreatedAt())
                .build();
    }

}
