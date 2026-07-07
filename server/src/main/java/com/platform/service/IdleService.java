package com.platform.service;

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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class IdleService {

    private final IdleItemRepository idleItemRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final RatingRepository ratingRepository;

    public IdleService(IdleItemRepository idleItemRepository,
                       UserRepository userRepository,
                       RoomRepository roomRepository,
                       BorrowRequestRepository borrowRequestRepository,
                       RatingRepository ratingRepository) {
        this.idleItemRepository = idleItemRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.ratingRepository = ratingRepository;
    }

    public IdleItemDTO publish(UUID userId, IdleItemRequest req) {
        IdleItem item = new IdleItem();
        item.setUserId(userId);
        item.setTitle(req.getTitle());
        item.setDescription(req.getDescription());
        item.setPostType(req.getPostType());
        item.setCategory(req.getCategory());
        item.setCondition(req.getCondition() != null ? req.getCondition() : "normal");
        item.setImages(req.getImages());
        item.setPrice(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO);
        item.setMaxDuration(req.getMaxDuration() != null ? req.getMaxDuration() : 7);
        item.setDurationUnit(req.getDurationUnit() != null ? req.getDurationUnit() : "day");
        item.setPickupMethod(req.getPickupMethod() != null ? req.getPickupMethod() : "self_pickup");
        item.setStatus("online");
        item.setCreatedAt(LocalDateTime.now());
        item = idleItemRepository.save(item);

        return toDTO(item);
    }

    public PageDTO<IdleItemDTO> getHomeList(String postType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<IdleItem> itemPage = idleItemRepository.findByStatusAndPostType("online", postType, pageRequest);

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

    public IdleItemDTO getDetail(UUID itemId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));
        return enrichWithUserStats(toDTO(item));
    }

    public PageDTO<IdleItemDTO> search(String keyword, String postType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<IdleItem> itemPage = idleItemRepository.findByStatusAndPostTypeAndTitleContainingOrDescriptionContaining(
                "online", postType, keyword, keyword, pageRequest);

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

    public List<IdleItemDTO> getMyPosts(UUID userId, String postType) {
        List<IdleItem> items = idleItemRepository
                .findByUserIdAndPostType(userId, postType, Pageable.unpaged())
                .getContent();
        return items.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public IdleItemDTO delist(UUID userId, UUID itemId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该物品");
        }

        item.setStatus("offline");
        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    public IdleItemDTO deleteItem(UUID userId, UUID itemId) {
        IdleItem item = idleItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("物品不存在"));

        if (!item.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该物品");
        }

        item.setStatus("deleted");
        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    /**
     * Update an idle item (edit save or relist).
     * If current status is completed/offline, auto-relists to online.
     */
    public IdleItemDTO update(UUID userId, UUID itemId, IdleItemRequest req) {
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

        // Auto-relist: completed/offline → online
        if ("completed".equals(item.getStatus()) || "offline".equals(item.getStatus())) {
            item.setStatus("online");
        }

        item = idleItemRepository.save(item);
        return toDTO(item);
    }

    private IdleItemDTO enrichWithUserStats(IdleItemDTO dto) {
        UUID userId = dto.getUserId();
        if (userId == null) return dto;

        Double avgScore = ratingRepository.getAverageScore(userId);
        // Default to 5.0 when no ratings yet (new user without mutual-help history)
        dto.setRating(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 5.0);

        long totalReturned = borrowRequestRepository.countReturnedByOwnerId(userId);
        dto.setLendCount(totalReturned);

        if (totalReturned > 0) {
            long onTime = borrowRequestRepository.countOnTimeReturnedByOwnerId(userId);
            dto.setReturnRate(Math.round(onTime * 100.0 / totalReturned) + "%");
        }
        return dto;
    }

    private IdleItemDTO toDTO(IdleItem item) {
        User user = userRepository.findById(item.getUserId()).orElse(null);
        String userName = user != null ? user.getName() : "未知用户";
        String roomInfo = formatRoom(user);

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
            case "owner": return "业主";
            case "resident":
            case "tenant": return "租客";
            default: return userType;
        }
    }
}
