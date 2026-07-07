package com.platform.service;

import com.platform.model.dto.MyPostItemDTO;
import com.platform.model.entity.BorrowRequest;
import com.platform.model.entity.HelpApplication;
import com.platform.model.entity.HelpRequest;
import com.platform.model.entity.IdleItem;
import com.platform.model.entity.Rating;
import com.platform.model.entity.User;
import com.platform.repository.BorrowRequestRepository;
import com.platform.repository.HelpApplicationRepository;
import com.platform.repository.HelpRequestRepository;
import com.platform.repository.IdleItemRepository;
import com.platform.repository.RatingRepository;
import com.platform.repository.RoomRepository;
import com.platform.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserActivityService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityService.class);

    private final IdleItemRepository idleItemRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final HelpApplicationRepository helpApplicationRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public UserActivityService(IdleItemRepository idleItemRepository,
                               HelpRequestRepository helpRequestRepository,
                               HelpApplicationRepository helpApplicationRepository,
                               BorrowRequestRepository borrowRequestRepository,
                               RatingRepository ratingRepository,
                               UserRepository userRepository,
                               RoomRepository roomRepository) {
        this.idleItemRepository = idleItemRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.helpApplicationRepository = helpApplicationRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    // ============================================================
    // 发布 tab — my published posts (idle + help)
    // ============================================================

    /**
     * Get my posts (combined idle + help) for the 发布 tab.
     * @param statusFilter "online" | "offline" | "completed" — filters items by status
     */
    public List<MyPostItemDTO> getMyPosts(UUID userId, String statusFilter) {
        List<MyPostItemDTO> result = new ArrayList<>();

        // 1. Idle items (all postTypes: LEND + WANTED)
        List<IdleItem> idleItems = idleItemRepository.findByUserId(userId);
        for (IdleItem item : idleItems) {
            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals(item.getStatus())) {
                continue;
            }
            result.add(toMyPostItemDTO(item));
        }

        // 2. Help requests
        List<HelpRequest> helpRequests = helpRequestRepository.findByUserId(userId);
        for (HelpRequest hr : helpRequests) {
            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals(hr.getStatus())) {
                continue;
            }
            result.add(toMyPostItemDTO(hr));
        }

        // 3. Sort by createdAt DESC
        result.sort(Comparator.comparing(MyPostItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    // ============================================================
    // 审批 tab — pending approvals
    // ============================================================

    /**
     * Get pending approvals (items awaiting my approval as owner).
     * @param type "borrow" | "help"
     */
    public List<MyPostItemDTO> getApprovals(UUID userId, String type) {
        List<MyPostItemDTO> result = new ArrayList<>();

        if ("borrow".equals(type)) {
            // Borrow requests on my idle items with status=pending
            List<BorrowRequest> pendingBorrows = borrowRequestRepository
                    .findByOwnerIdAndStatus(userId, "pending");
            for (BorrowRequest br : pendingBorrows) {
                MyPostItemDTO dto = borrowRequestToDTO(br);
                // Populate borrower (peer) info — this was missing, causing blank 住户名
                User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);
                if (borrower != null) {
                    dto.setPersonName(formatPersonName(borrower));
                    dto.setPersonRoom(formatRoom(borrower));
                    dto.setPersonType(getUserTypeLabel(borrower.getUserType()));
                }
                enrichUserStats(dto, br.getBorrowerId());
                dto.setType("idle");
                dto.setSubType("borrow");
                dto.setPostType("LEND");
                result.add(dto);
            }
        } else if ("help".equals(type)) {
            // Help applications on my help requests with status=pending
            List<HelpRequest> myHelpRequests = helpRequestRepository.findByUserId(userId);
            for (HelpRequest hr : myHelpRequests) {
                List<HelpApplication> pendingApps = helpApplicationRepository
                        .findByHelpIdAndStatus(hr.getId(), "pending");
                for (HelpApplication app : pendingApps) {
                    MyPostItemDTO dto = helpRequestToDTO(hr);
                    dto.setId(app.getId()); // use application id as the primary id
                    dto.setType("help");
                    dto.setSubType("helpReq");
                    dto.setPostType("HELP");

                    // Peer (helper) info
                    User helper = userRepository.findById(app.getHelperId()).orElse(null);
                    if (helper != null) {
                        dto.setPersonName(formatPersonName(helper));
                        dto.setPersonRoom(formatRoom(helper));
                        dto.setPersonType(getUserTypeLabel(helper.getUserType()));
                    }
                    enrichUserStats(dto, app.getHelperId());
                    result.add(dto);
                }
            }
        }

        result.sort(Comparator.comparing(MyPostItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // ============================================================
    // 进行中 tab — in-progress transactions
    // ============================================================

    /**
     * Get in-progress transactions.
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    public List<MyPostItemDTO> getInProgress(UUID userId, String role) {
        List<MyPostItemDTO> result = new ArrayList<>();

        switch (role) {
            case "borrow" -> {
                // I am borrowing someone else's item
                List<BorrowRequest> borrows = borrowRequestRepository
                        .findByBorrowerIdAndStatus(userId, "approved");
                for (BorrowRequest br : borrows) {
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType("borrow");
                    dto.setPostType("LEND");
                    dto.setRoleLabel("借走住户");
                    populatePeerFromIdleOwner(dto, br);
                    calculateRemaining(dto, br);
                    result.add(dto);
                }
            }
            case "lend" -> {
                // Someone is borrowing my item
                List<BorrowRequest> lends = borrowRequestRepository
                        .findByOwnerIdAndStatus(userId, "approved");
                for (BorrowRequest br : lends) {
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType("lend");
                    dto.setPostType("LEND");
                    dto.setRoleLabel("借出住户");
                    // Peer is the borrower
                    User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);
                    if (borrower != null) {
                        dto.setPersonName(formatPersonName(borrower));
                        dto.setPersonRoom(formatRoom(borrower));
                        dto.setPersonType(getUserTypeLabel(borrower.getUserType()));
                    }
                    calculateRemaining(dto, br);
                    result.add(dto);
                }
            }
            case "helpReq" -> {
                // I requested help and someone is helping me
                List<HelpRequest> myHelpRequests = helpRequestRepository.findByUserId(userId);
                for (HelpRequest hr : myHelpRequests) {
                    List<HelpApplication> approvedApps = helpApplicationRepository
                            .findByHelpIdAndStatus(hr.getId(), "approved");
                    for (HelpApplication app : approvedApps) {
                        MyPostItemDTO dto = helpRequestToDTO(hr);
                        dto.setId(app.getId());
                        dto.setType("help");
                        dto.setSubType("helpReq");
                        dto.setPostType("HELP");
                        dto.setRoleLabel("求助住户");
                        dto.setDisplayStatus("进行中");
                        User helper = userRepository.findById(app.getHelperId()).orElse(null);
                        if (helper != null) {
                            dto.setPersonName(formatPersonName(helper));
                            dto.setPersonRoom(formatRoom(helper));
                            dto.setPersonType(getUserTypeLabel(helper.getUserType()));
                        }
                        calculateHelpRemaining(dto, hr);
                        result.add(dto);
                    }
                }
            }
            case "helpPro" -> {
                // I am helping someone
                List<HelpApplication> myApps = helpApplicationRepository.findByHelperId(userId);
                for (HelpApplication app : myApps) {
                    if (!"approved".equals(app.getStatus())) continue;
                    HelpRequest hr = app.getHelpRequest();
                    if (hr == null) {
                        hr = helpRequestRepository.findById(app.getHelpId()).orElse(null);
                    }
                    if (hr == null) continue;
                    MyPostItemDTO dto = helpRequestToDTO(hr);
                    dto.setId(app.getId());
                    dto.setType("help");
                    dto.setSubType("helpPro");
                    dto.setPostType("HELP");
                    dto.setRoleLabel("帮助住户");
                    dto.setDisplayStatus("进行中");
                    // Peer is the help requester
                    User requester = userRepository.findById(hr.getUserId()).orElse(null);
                    if (requester != null) {
                        dto.setPersonName(formatPersonName(requester));
                        dto.setPersonRoom(formatRoom(requester));
                        dto.setPersonType(getUserTypeLabel(requester.getUserType()));
                    }
                    calculateHelpRemaining(dto, hr);
                    result.add(dto);
                }
            }
            default -> throw new RuntimeException("无效的角色类型: " + role);
        }

        result.sort(Comparator.comparing(MyPostItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // ============================================================
    // 已完成 tab — completed transactions
    // ============================================================

    /**
     * Get completed transactions.
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    public List<MyPostItemDTO> getCompleted(UUID userId, String role) {
        List<MyPostItemDTO> result = new ArrayList<>();

        switch (role) {
            case "borrow" -> {
                // I borrowed and returned
                List<BorrowRequest> borrows = borrowRequestRepository
                        .findByBorrowerIdAndStatus(userId, "returned");
                for (BorrowRequest br : borrows) {
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType("borrow");
                    dto.setPostType("LEND");
                    dto.setRoleLabel("借走住户");
                    dto.setCompletedAt(br.getUpdatedAt());
                    populatePeerFromIdleOwner(dto, br);
                    loadBorrowRatings(dto, br, userId);
                    result.add(dto);
                }
            }
            case "lend" -> {
                // Someone borrowed my item and returned
                List<BorrowRequest> lends = borrowRequestRepository
                        .findByOwnerIdAndStatus(userId, "returned");
                for (BorrowRequest br : lends) {
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType("lend");
                    dto.setPostType("LEND");
                    dto.setRoleLabel("借出住户");
                    dto.setCompletedAt(br.getUpdatedAt());
                    User borrower = userRepository.findById(br.getBorrowerId()).orElse(null);
                    if (borrower != null) {
                        dto.setPersonName(formatPersonName(borrower));
                        dto.setPersonRoom(formatRoom(borrower));
                        dto.setPersonType(getUserTypeLabel(borrower.getUserType()));
                    }
                    loadBorrowRatings(dto, br, userId);
                    result.add(dto);
                }
            }
            case "helpReq" -> {
                // My help request was completed
                List<HelpRequest> myHelpRequests = helpRequestRepository.findByUserId(userId);
                for (HelpRequest hr : myHelpRequests) {
                    List<HelpApplication> completedApps = helpApplicationRepository
                            .findByHelpIdAndStatus(hr.getId(), "completed");
                    for (HelpApplication app : completedApps) {
                        MyPostItemDTO dto = helpRequestToDTO(hr);
                        dto.setId(app.getId());
                        dto.setType("help");
                        dto.setSubType("helpReq");
                        dto.setPostType("HELP");
                        dto.setRoleLabel("求助住户");
                        dto.setCompletedAt(app.getCompletedAt());
                        User helper = userRepository.findById(app.getHelperId()).orElse(null);
                        if (helper != null) {
                            dto.setPersonName(formatPersonName(helper));
                            dto.setPersonRoom(formatRoom(helper));
                            dto.setPersonType(getUserTypeLabel(helper.getUserType()));
                        }
                        loadHelpRatings(dto, app, userId);
                        result.add(dto);
                    }
                }
            }
            case "helpPro" -> {
                // I helped and it was completed
                List<HelpApplication> myApps = helpApplicationRepository.findByHelperId(userId);
                for (HelpApplication app : myApps) {
                    if (!"completed".equals(app.getStatus())) continue;
                    HelpRequest hr = app.getHelpRequest();
                    if (hr == null) {
                        hr = helpRequestRepository.findById(app.getHelpId()).orElse(null);
                    }
                    if (hr == null) continue;
                    MyPostItemDTO dto = helpRequestToDTO(hr);
                    dto.setId(app.getId());
                    dto.setType("help");
                    dto.setSubType("helpPro");
                    dto.setPostType("HELP");
                    dto.setRoleLabel("帮助住户");
                    dto.setCompletedAt(app.getCompletedAt());
                    User requester = userRepository.findById(hr.getUserId()).orElse(null);
                    if (requester != null) {
                        dto.setPersonName(formatPersonName(requester));
                        dto.setPersonRoom(formatRoom(requester));
                        dto.setPersonType(getUserTypeLabel(requester.getUserType()));
                    }
                    loadHelpRatings(dto, app, userId);
                    result.add(dto);
                }
            }
            default -> throw new RuntimeException("无效的角色类型: " + role);
        }

        result.sort(Comparator.comparing(MyPostItemDTO::getCompletedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    // ============================================================
    // Private conversion helpers
    // ============================================================

    /**
     * Convert an IdleItem to MyPostItemDTO (for 发布 tab).
     */
    private MyPostItemDTO toMyPostItemDTO(IdleItem item) {
        String displayStatus = mapIdleDisplayStatus(item.getStatus());

        User user = item.getUser();
        String personName = formatPersonName(user);
        String personRoom = formatRoom(user);
        String personType = user != null ? getUserTypeLabel(user.getUserType()) : null;

        return MyPostItemDTO.builder()
                .id(item.getId())
                .type("idle")
                .postType(item.getPostType())
                .title(item.getTitle())
                .category(item.getCategory())
                .description(item.getDescription())
                .price(item.getPrice())
                .condition(item.getCondition())
                .maxDuration(item.getMaxDuration())
                .durationUnit(item.getDurationUnit())
                .pickupMethod(item.getPickupMethod())
                .isProxy(item.getIsProxy())
                .status(item.getStatus())
                .displayStatus(displayStatus)
                .createdAt(item.getCreatedAt())
                .personName(personName)
                .personRoom(personRoom)
                .personType(personType)
                .build();
    }

    /**
     * Convert a HelpRequest to MyPostItemDTO (for 发布 tab).
     */
    private MyPostItemDTO toMyPostItemDTO(HelpRequest hr) {
        String displayStatus = mapHelpDisplayStatus(hr.getStatus());

        User user = hr.getUser();
        String personName = formatPersonName(user);
        String personRoom = formatRoom(user);
        String personType = user != null ? getUserTypeLabel(user.getUserType()) : null;

        return MyPostItemDTO.builder()
                .id(hr.getId())
                .type("help")
                .postType("HELP")
                .title(hr.getTitle())
                .category(hr.getCategory())
                .description(hr.getDescription())
                .isUrgent(hr.getIsUrgent())
                .isProxy(hr.getIsProxy())
                .status(hr.getStatus())
                .displayStatus(displayStatus)
                .createdAt(hr.getCreatedAt())
                .personName(personName)
                .personRoom(personRoom)
                .personType(personType)
                .build();
    }

    /**
     * Base DTO from a HelpRequest (without type/subType — caller sets those).
     */
    private MyPostItemDTO helpRequestToDTO(HelpRequest hr) {
        return MyPostItemDTO.builder()
                .title(hr.getTitle())
                .category(hr.getCategory())
                .description(hr.getDescription())
                .isUrgent(hr.getIsUrgent())
                .isProxy(hr.getIsProxy())
                .status(hr.getStatus())
                .displayStatus(mapHelpDisplayStatus(hr.getStatus()))
                .createdAt(hr.getCreatedAt())
                .build();
    }

    /**
     * Base DTO from a BorrowRequest (without type/subType — caller sets those).
     */
    private MyPostItemDTO borrowRequestToDTO(BorrowRequest br) {
        IdleItem idleItem = br.getIdleItem();
        if (idleItem == null) {
            idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
        }

        String title = idleItem != null ? idleItem.getTitle() : "未知物品";
        String category = idleItem != null ? idleItem.getCategory() : null;
        String description = idleItem != null ? idleItem.getDescription() : null;

        return MyPostItemDTO.builder()
                .id(br.getId())
                .title(title)
                .category(category)
                .description(description)
                .price(idleItem != null ? idleItem.getPrice() : null)
                .condition(idleItem != null ? idleItem.getCondition() : null)
                .maxDuration(br.getDurationDays())
                .durationUnit(br.getDurationType())
                .createdAt(br.getCreatedAt())
                .build();
    }

    // ============================================================
    // Display status mapping
    // ============================================================

    private String mapIdleDisplayStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "online" -> "在线";
            case "offline" -> "已下架";
            case "borrowing" -> "进行中";
            case "completed" -> "已完成";
            default -> status;
        };
    }

    private String mapHelpDisplayStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "online" -> "在线";
            case "offline" -> "已下架";
            case "helping" -> "进行中";
            case "completed" -> "已完成";
            default -> status;
        };
    }

    // ============================================================
    // Remaining days calculation
    // ============================================================

    private void calculateRemaining(MyPostItemDTO dto, BorrowRequest br) {
        if (br.getStartDate() == null || br.getDurationDays() == null) return;

        LocalDate expectedReturn = br.getStartDate().plusDays(br.getDurationDays());
        long remaining = ChronoUnit.DAYS.between(LocalDate.now(), expectedReturn);

        dto.setExpectedReturnDays(br.getDurationDays());
        dto.setRemainingDays((int) remaining);
        dto.setIsOverdue(remaining < 0);

        if (remaining > 0) {
            dto.setMetaText("剩余 " + remaining + " 天归还");
        } else if (remaining == 0) {
            dto.setMetaText("今日应归还");
        } else {
            dto.setMetaText("已逾期 " + Math.abs(remaining) + " 天");
        }
    }

    private void calculateHelpRemaining(MyPostItemDTO dto, HelpRequest hr) {
        if (hr.getTimeEnd() == null) return;

        long remaining = ChronoUnit.DAYS.between(LocalDate.now(), hr.getTimeEnd().toLocalDate());
        dto.setRemainingDays((int) remaining);
        dto.setIsOverdue(remaining < 0);

        if (remaining > 0) {
            dto.setMetaText("剩余 " + remaining + " 天");
        } else if (remaining == 0) {
            dto.setMetaText("今日截止");
        } else {
            dto.setMetaText("已逾期 " + Math.abs(remaining) + " 天");
        }
    }

    // ============================================================
    // Peer info population
    // ============================================================

    /**
     * Populate peer info from the owner of the idle item in a borrow request.
     */
    private void populatePeerFromIdleOwner(MyPostItemDTO dto, BorrowRequest br) {
        IdleItem idleItem = br.getIdleItem();
        if (idleItem == null) {
            idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
        }
        if (idleItem == null) return;

        User owner = idleItem.getUser();
        if (owner == null && idleItem.getUserId() != null) {
            owner = userRepository.findById(idleItem.getUserId()).orElse(null);
        }
        if (owner != null) {
            dto.setPersonName(formatPersonName(owner));
            dto.setPersonRoom(formatRoom(owner));
            dto.setPersonType(getUserTypeLabel(owner.getUserType()));
        }
    }

    // ============================================================
    // Rating loading
    // ============================================================

    private void loadBorrowRatings(MyPostItemDTO dto, BorrowRequest br, UUID currentUserId) {
        // My rating of the other party
        Optional<Rating> myRating = ratingRepository.findByBorrowIdAndFromUserId(br.getId(), currentUserId);
        myRating.ifPresent(r -> {
            dto.setMyRating(r.getScore().doubleValue());
            dto.setMyFeedback(r.getDimensionScores());
        });

        // Other party's rating of me
        UUID idleOwnerId = br.getIdleItem() != null ? br.getIdleItem().getUserId() : null;
        if (idleOwnerId == null) {
            // Lazy-loading fallback
            idleOwnerId = idleItemRepository.findById(br.getIdleId())
                    .map(IdleItem::getUserId).orElse(null);
        }
        UUID otherUserId = currentUserId.equals(br.getBorrowerId())
                ? idleOwnerId
                : br.getBorrowerId();
        if (otherUserId != null) {
            Optional<Rating> theirRating = ratingRepository.findByBorrowIdAndFromUserId(br.getId(), otherUserId);
            theirRating.ifPresent(r -> {
                dto.setTheirRating(r.getScore().doubleValue());
                dto.setTheirFeedback(r.getDimensionScores());
            });
        }
    }

    private void loadHelpRatings(MyPostItemDTO dto, HelpApplication app, UUID currentUserId) {
        // My rating of the other party
        Optional<Rating> myRating = ratingRepository.findByHelpApplicationIdAndFromUserId(app.getId(), currentUserId);
        myRating.ifPresent(r -> {
            dto.setMyRating(r.getScore().doubleValue());
            dto.setMyFeedback(r.getDimensionScores());
        });

        // Other party's rating of me
        HelpRequest hr = app.getHelpRequest();
        if (hr == null) {
            hr = helpRequestRepository.findById(app.getHelpId()).orElse(null);
        }
        UUID otherUserId = currentUserId.equals(app.getHelperId())
                ? (hr != null ? hr.getUserId() : null)
                : app.getHelperId();
        if (otherUserId != null) {
            Optional<Rating> theirRating = ratingRepository.findByHelpApplicationIdAndFromUserId(app.getId(), otherUserId);
            theirRating.ifPresent(r -> {
                dto.setTheirRating(r.getScore().doubleValue());
                dto.setTheirFeedback(r.getDimensionScores());
            });
        }
    }

    // ============================================================
    // User stats enrichment
    // ============================================================

    /**
     * Enrich DTO with user stats (rating, borrow/lend/help counts).
     */
    private void enrichUserStats(MyPostItemDTO dto, UUID userId) {
        if (userId == null) return;

        // Rating
        Double avgScore = ratingRepository.getAverageScore(userId);
        dto.setPersonRating(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null);

        // Borrow count
        List<BorrowRequest> allBorrows = borrowRequestRepository.findByBorrowerId(userId);
        dto.setBorrowCount(allBorrows != null ? allBorrows.size() : 0);

        // Borrow return rate (on-time returns / total returns)
        if (allBorrows != null && !allBorrows.isEmpty()) {
            long returnedCount = allBorrows.stream()
                    .filter(b -> "returned".equals(b.getStatus())).count();
            long onTimeCount = allBorrows.stream()
                    .filter(b -> "returned".equals(b.getStatus()) && Boolean.TRUE.equals(b.getIsOnTime()))
                    .count();
            dto.setBorrowReturnRate(returnedCount > 0
                    ? Math.round((double) onTimeCount / returnedCount * 1000.0) / 10.0
                    : null);
        }

        // Lend count (idle items posted by this user)
        List<IdleItem> myIdleItems = idleItemRepository.findByUserId(userId);
        long lendCount = myIdleItems != null
                ? myIdleItems.stream().filter(i -> "LEND".equals(i.getPostType())).count()
                : 0;
        dto.setLendCount((int) lendCount);

        // Help request count
        List<HelpRequest> myHelpReqs = helpRequestRepository.findByUserId(userId);
        dto.setHelpReqCount(myHelpReqs != null ? myHelpReqs.size() : 0);

        // Help provide count (approved help applications)
        long helpProCount = helpApplicationRepository.countByHelperIdAndStatus(userId, "approved");
        dto.setHelpProCount((int) helpProCount);
    }

    // ============================================================
    // Room formatting (same pattern as HelpService)
    // ============================================================

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

            return buildingName + unitName + roomNumber + "号";
        } catch (Exception e) {
            log.debug("Failed to format room for user {}: {}", user.getId(), e.getMessage());
            return "";
        }
    }

    private String formatPersonName(User user) {
        if (user == null) return "未知用户";
        String roomPart = formatRoom(user);
        String typeLabel = getUserTypeLabel(user.getUserType());
        if (!roomPart.isEmpty()) {
            return roomPart + "(" + typeLabel + ")";
        }
        String name = user.getName() != null ? user.getName() : "未知用户";
        return name + "(" + typeLabel + ")";
    }

    private String getUserTypeLabel(String userType) {
        if (userType == null) return "";
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
}
