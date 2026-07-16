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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    // 个人资料 — 我的 tab
    // ============================================================

    /**
     * 获取当前用户的个人资料及统计数据，用于"我的" tab。
     */
    public java.util.Map<String, Object> getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        java.util.Map<String, Object> profile = new java.util.HashMap<>();

        // 基本信息
        profile.put("id", user.getId());
        profile.put("name", user.getName());
        profile.put("userType", user.getUserType());
        profile.put("userTypeText", getUserTypeLabel(user.getUserType()));
        profile.put("roomInfo", formatRoom(user));
        profile.put("isAuth", "approved".equals(user.getAuthStatus()));

        // 评分
        Double avgScore = ratingRepository.getAverageScore(userId);
        profile.put("score", avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0);
        long ratingCount = ratingRepository.countByToUserId(userId);
        profile.put("ratingCount", (int) ratingCount);

        // 统计数据
        long lendCount = idleItemRepository.findByUserId(userId).stream()
                .filter(i -> "LEND".equals(i.getPostType())).count();
        profile.put("lendCount", (int) lendCount);

        // 借入次数按"真实角色"统计：WANTED 帖里发布者才是借入方，响应者其实是出借方，
        // 直接用 borrowerId 会把方向算反，故统一走 borrowRoleCounts。
        int[] bl = borrowRoleCounts(userId);
        profile.put("borrowCount", bl[0]);
        // 尚无已归还的互借记录时默认 100%（新用户无扣分依据）
        profile.put("borrowReturnRate", bl[1] > 0
                ? Math.round((double) bl[2] / bl[1] * 1000.0) / 10.0 : 100.0);

        List<HelpRequest> helpReqs = helpRequestRepository.findByUserId(userId);
        profile.put("helpReqCount", helpReqs != null ? helpReqs.size() : 0);

        long helpProCount = helpApplicationRepository.countByHelperIdAndStatus(userId, "approved");
        profile.put("helpProCount", (int) helpProCount);

        return profile;
    }

    // ============================================================
    // 发布 tab — 我发布的帖子（闲置 + 求助）
    // ============================================================

    /**
     * 获取我发布的帖子（闲置 + 求助合并），用于"发布" tab。
     * @param statusFilter "online" | "offline" | "completed" — 按状态过滤
     */
    public List<MyPostItemDTO> getMyPosts(Long userId, String statusFilter) {
        List<MyPostItemDTO> result = new ArrayList<>();

        // 1. 闲置物品（所有 postType：LEND + WANTED）
        List<IdleItem> idleItems = idleItemRepository.findByUserId(userId);
        for (IdleItem item : idleItems) {
            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals(item.getStatus())) {
                continue;
            }
            result.add(toMyPostItemDTO(item));
        }

        // 2. 求助信息
        List<HelpRequest> helpRequests = helpRequestRepository.findByUserId(userId);
        for (HelpRequest hr : helpRequests) {
            if (statusFilter != null && !statusFilter.isEmpty() && !statusFilter.equals(hr.getStatus())) {
                continue;
            }
            result.add(toMyPostItemDTO(hr));
        }

        // 3. 按 createdAt 降序排序
        result.sort(Comparator.comparing(MyPostItemDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        return result;
    }

    // ============================================================
    // 审批 tab — 待审批事项
    // ============================================================

    /**
     * 获取待审批事项（等待我作为物品发布者审批的申请）。
     * @param type "borrow" | "lend" | "help"
     *   - borrow: 别人申请借入我发布的 LEND 物品（我是出借方，审批是否借出）
     *   - lend:   别人愿意借出给我发布的 WANTED 需求（我是借入方，确认是否借入）
     *   - help:   别人申请帮助我发布的求助
     */
    public List<MyPostItemDTO> getApprovals(Long userId, String type) {
        List<MyPostItemDTO> result = new ArrayList<>();

        if ("borrow".equals(type) || "lend".equals(type)) {
            // 两者都是"我作为物品 owner 的待处理申请"，仅 postType 不同：
            // borrow → LEND 帖；lend → WANTED 帖（需求借入的借出意向）。
            boolean wantWanted = "lend".equals(type);
            List<BorrowRequest> pendingBorrows = borrowRequestRepository
                    .findByOwnerIdAndStatus(userId, "pending");
            for (BorrowRequest br : pendingBorrows) {
                IdleItem item = resolveIdleItem(br);
                boolean wanted = item != null && "WANTED".equals(item.getPostType());
                if (wanted != wantWanted) continue;
                MyPostItemDTO dto = borrowRequestToDTO(br);
                // 对方（申请人 = borrowerId）：borrow 里是借入者，lend 里是出借者
                User applicant = userRepository.findById(br.getBorrowerId()).orElse(null);
                if (applicant != null) {
                    dto.setPersonName(formatPersonName(applicant));
                    dto.setPersonRoom(formatRoom(applicant));
                    dto.setPersonType(getUserTypeLabel(applicant.getUserType()));
                }
                enrichUserStats(dto, br.getBorrowerId());
                dto.setType("idle");
                dto.setSubType(type);
                result.add(dto);
            }
        } else if ("help".equals(type)) {
            // 我发布的求助下 status=pending 的帮助申请
            List<HelpRequest> myHelpRequests = helpRequestRepository.findByUserId(userId);
            for (HelpRequest hr : myHelpRequests) {
                List<HelpApplication> pendingApps = helpApplicationRepository
                        .findByHelpIdAndStatus(hr.getId(), "pending");
                for (HelpApplication app : pendingApps) {
                    MyPostItemDTO dto = helpRequestToDTO(hr);
                    dto.setId(app.getId()); // 使用申请 ID 作为主 ID
                    dto.setType("help");
                    dto.setSubType("helpReq");
                    dto.setPostType("HELP");
                    dto.setNote(app.getNote()); // 帮助说明（帮助者申请时填写）

                    // 对方（帮助者）信息
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
    // 进行中 tab — 进行中的交易
    // ============================================================

    /**
     * 获取进行中的交易。
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    public List<MyPostItemDTO> getInProgress(Long userId, String role) {
        List<MyPostItemDTO> result = new ArrayList<>();

        switch (role) {
            case "borrow", "lend" -> {
                // 借入与借出是同一批 BorrowRequest 的两个视角。收集所有与我相关（我是
                // 物品 owner 或 borrower）且已通过的记录，按"真实角色"分流到对应子 tab。
                // WANTED 帖会把 owner/borrower 的借入借出关系反转，故必须用 resolveBorrowRole。
                List<BorrowRequest> pool = new ArrayList<>();
                pool.addAll(borrowRequestRepository.findByBorrowerIdAndStatus(userId, "approved"));
                pool.addAll(borrowRequestRepository.findByOwnerIdAndStatus(userId, "approved"));
                Set<Long> seen = new HashSet<>();
                for (BorrowRequest br : pool) {
                    if (!seen.add(br.getId())) continue;
                    if (!role.equals(resolveBorrowRole(br, userId))) continue;
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType(role);
                    // borrow：对方是出借住户；lend：对方是借走住户
                    dto.setRoleLabel(role.equals("borrow") ? "借出住户" : "借走住户");
                    populateBorrowPeer(dto, br, userId);
                    calculateRemaining(dto, br);
                    result.add(dto);
                }
            }
            case "helpReq" -> {
                // 我发起求助且有人正在帮我
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
                        dto.setNote(app.getNote()); // 帮助说明
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
                // 我正在帮助别人
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
                    dto.setNote(app.getNote()); // 帮助说明
                    // 对方是求助发起者
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
    // 已完成 tab — 已完成的交易
    // ============================================================

    /**
     * 获取已完成的交易。
     * @param role "borrow" | "lend" | "helpReq" | "helpPro"
     */
    public List<MyPostItemDTO> getCompleted(Long userId, String role) {
        List<MyPostItemDTO> result = new ArrayList<>();

        switch (role) {
            case "borrow", "lend" -> {
                // 已归还的互借记录，按真实角色分流（含 WANTED 角色反转）。
                List<BorrowRequest> pool = new ArrayList<>();
                pool.addAll(borrowRequestRepository.findByBorrowerIdAndStatus(userId, "returned"));
                pool.addAll(borrowRequestRepository.findByOwnerIdAndStatus(userId, "returned"));
                Set<Long> seen = new HashSet<>();
                for (BorrowRequest br : pool) {
                    if (!seen.add(br.getId())) continue;
                    if (!role.equals(resolveBorrowRole(br, userId))) continue;
                    MyPostItemDTO dto = borrowRequestToDTO(br);
                    dto.setType("idle");
                    dto.setSubType(role);
                    dto.setRoleLabel(role.equals("borrow") ? "借出住户" : "借走住户");
                    dto.setCompletedAt(br.getUpdatedAt());
                    populateBorrowPeer(dto, br, userId);
                    loadBorrowRatings(dto, br, userId);
                    result.add(dto);
                }
            }
            case "helpReq" -> {
                // 我发布的求助已完成
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
                        dto.setNote(app.getNote()); // 帮助说明
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
                // 我提供的帮助已完成
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
                    dto.setNote(app.getNote()); // 帮助说明
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
    // 私有转换辅助方法
    // ============================================================

    /**
     * 将 IdleItem 转换为 MyPostItemDTO（用于"发布" tab）。
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
     * 将 HelpRequest 转换为 MyPostItemDTO（用于"发布" tab）。
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
     * 由 HelpRequest 构建基础 DTO（不含 type/subType，由调用方设置）。
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
     * 由 BorrowRequest 构建基础 DTO（不含 type/subType，由调用方设置）。
     */
    private MyPostItemDTO borrowRequestToDTO(BorrowRequest br) {
        IdleItem idleItem = br.getIdleItem();
        if (idleItem == null) {
            idleItem = idleItemRepository.findById(br.getIdleId()).orElse(null);
        }

        String title = idleItem != null ? idleItem.getTitle() : "未知物品";
        String category = idleItem != null ? idleItem.getCategory() : null;
        String description = idleItem != null ? idleItem.getDescription() : null;
        // postType 取自原始闲置帖：LEND → 借入说明, WANTED → 借出说明
        String postType = idleItem != null ? idleItem.getPostType() : "LEND";

        return MyPostItemDTO.builder()
                .id(br.getId())
                .title(title)
                .category(category)
                .description(description)
                .postType(postType)
                .price(idleItem != null ? idleItem.getPrice() : null)
                .condition(idleItem != null ? idleItem.getCondition() : null)
                .maxDuration(br.getDurationDays())
                .durationUnit(br.getDurationType())
                .note(br.getNote())
                .createdAt(br.getCreatedAt())
                .build();
    }

    // ============================================================
    // 显示状态映射
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
    // 剩余天数计算
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
    // 对方信息填充
    // ============================================================

    /**
     * 用借用申请对应闲置物品的发布者信息填充对方信息。
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
    // 借用角色判定 — 处理 WANTED(需求借入) 的角色反转
    //
    // 一条 BorrowRequest 涉及两方：物品 owner(idleItem.userId) 与 borrowerId。
    //  - LEND 帖：owner 是出借方，borrowerId 是借入方（与模型一致）。
    //  - WANTED 帖：发布者(owner) 其实是"借入方"，响应者(borrowerId) 才是"出借方"，
    //    真实角色相对模型反转。
    // 因此判断"当前用户在这笔交易里到底是借入还是借出"必须结合 postType。
    // ============================================================

    private IdleItem resolveIdleItem(BorrowRequest br) {
        IdleItem item = br.getIdleItem();
        if (item == null) {
            item = idleItemRepository.findById(br.getIdleId()).orElse(null);
        }
        return item;
    }

    /**
     * 计算当前用户在这笔 BorrowRequest 中的真实角色。
     * @return "borrow"（我是借入方）或 "lend"（我是出借方）
     */
    private String resolveBorrowRole(BorrowRequest br, Long me) {
        IdleItem item = resolveIdleItem(br);
        boolean wanted = item != null && "WANTED".equals(item.getPostType());
        boolean iAmOwner = item != null && me.equals(item.getUserId());
        // LEND+owner→lend, LEND+borrower→borrow, WANTED+owner→borrow, WANTED+borrower→lend
        if (iAmOwner) {
            return wanted ? "borrow" : "lend";
        }
        return wanted ? "lend" : "borrow";
    }

    /**
     * 把 DTO 的 person* 字段填成"对方"（这笔交易里非当前用户的另一方）。
     */
    private void populateBorrowPeer(MyPostItemDTO dto, BorrowRequest br, Long me) {
        IdleItem item = resolveIdleItem(br);
        Long ownerId = item != null ? item.getUserId() : null;
        Long peerId = me.equals(br.getBorrowerId()) ? ownerId : br.getBorrowerId();
        if (peerId == null) return;
        User peer = userRepository.findById(peerId).orElse(null);
        if (peer != null) {
            dto.setPersonName(formatPersonName(peer));
            dto.setPersonRoom(formatRoom(peer));
            dto.setPersonType(getUserTypeLabel(peer.getUserType()));
        }
    }

    /**
     * 按真实角色统计当前用户的"借入"交易。
     * @return [borrowCount(approved+returned), returnedCount, onTimeCount]
     */
    private int[] borrowRoleCounts(Long userId) {
        List<BorrowRequest> pool = new ArrayList<>();
        pool.addAll(borrowRequestRepository.findByBorrowerId(userId));
        pool.addAll(borrowRequestRepository.findByOwnerIdAndStatus(userId, "approved"));
        pool.addAll(borrowRequestRepository.findByOwnerIdAndStatus(userId, "returned"));

        Set<Long> seen = new HashSet<>();
        int borrowCount = 0, returnedCount = 0, onTimeCount = 0;
        for (BorrowRequest br : pool) {
            if (!seen.add(br.getId())) continue;
            if (!"borrow".equals(resolveBorrowRole(br, userId))) continue;
            String st = br.getStatus();
            if ("approved".equals(st) || "returned".equals(st)) borrowCount++;
            if ("returned".equals(st)) {
                returnedCount++;
                if (Boolean.TRUE.equals(br.getIsOnTime())) onTimeCount++;
            }
        }
        return new int[]{borrowCount, returnedCount, onTimeCount};
    }

    // ============================================================
    // 评价加载
    // ============================================================

    private void loadBorrowRatings(MyPostItemDTO dto, BorrowRequest br, Long currentUserId) {
        // 我对对方的评价
        Optional<Rating> myRating = ratingRepository.findByBorrowIdAndFromUserId(br.getId(), currentUserId);
        myRating.ifPresent(r -> {
            dto.setMyRating(r.getScore().doubleValue());
            dto.setMyFeedback(null);
        });

        // 对方对我的评价
        Long idleOwnerId = br.getIdleItem() != null ? br.getIdleItem().getUserId() : null;
        if (idleOwnerId == null) {
            // 懒加载失败时的兜底查询
            idleOwnerId = idleItemRepository.findById(br.getIdleId())
                    .map(IdleItem::getUserId).orElse(null);
        }
        Long otherUserId = currentUserId.equals(br.getBorrowerId())
                ? idleOwnerId
                : br.getBorrowerId();
        if (otherUserId != null) {
            Optional<Rating> theirRating = ratingRepository.findByBorrowIdAndFromUserId(br.getId(), otherUserId);
            theirRating.ifPresent(r -> {
                dto.setTheirRating(r.getScore().doubleValue());
                dto.setTheirFeedback(null);
            });
        }
    }

    private void loadHelpRatings(MyPostItemDTO dto, HelpApplication app, Long currentUserId) {
        // 我对对方的评价
        Optional<Rating> myRating = ratingRepository.findByHelpApplicationIdAndFromUserId(app.getId(), currentUserId);
        myRating.ifPresent(r -> {
            dto.setMyRating(r.getScore().doubleValue());
            dto.setMyFeedback(null);
        });

        // 对方对我的评价
        HelpRequest hr = app.getHelpRequest();
        if (hr == null) {
            hr = helpRequestRepository.findById(app.getHelpId()).orElse(null);
        }
        Long otherUserId = currentUserId.equals(app.getHelperId())
                ? (hr != null ? hr.getUserId() : null)
                : app.getHelperId();
        if (otherUserId != null) {
            Optional<Rating> theirRating = ratingRepository.findByHelpApplicationIdAndFromUserId(app.getId(), otherUserId);
            theirRating.ifPresent(r -> {
                dto.setTheirRating(r.getScore().doubleValue());
                dto.setTheirFeedback(null);
            });
        }
    }

    // ============================================================
    // 用户统计数据补充
    // ============================================================

    /**
     * 为 DTO 补充用户统计数据（评分、借入/借出/求助次数）。
     */
    private void enrichUserStats(MyPostItemDTO dto, Long userId) {
        if (userId == null) return;

        // 评分
        Double avgScore = ratingRepository.getAverageScore(userId);
        dto.setPersonRating(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : null);

        // 借入次数 / 归还率 — 按真实角色统计（含 WANTED 角色反转）
        int[] bl = borrowRoleCounts(userId);
        dto.setBorrowCount(bl[0]);
        // 尚无已归还的互借记录时默认 100%
        dto.setBorrowReturnRate(bl[1] > 0
                ? Math.round((double) bl[2] / bl[1] * 1000.0) / 10.0
                : 100.0);

        // 借出次数（该用户发布的闲置物品）
        List<IdleItem> myIdleItems = idleItemRepository.findByUserId(userId);
        long lendCount = myIdleItems != null
                ? myIdleItems.stream().filter(i -> "LEND".equals(i.getPostType())).count()
                : 0;
        dto.setLendCount((int) lendCount);

        // 求助次数
        List<HelpRequest> myHelpReqs = helpRequestRepository.findByUserId(userId);
        dto.setHelpReqCount(myHelpReqs != null ? myHelpReqs.size() : 0);

        // 提供帮助次数（已通过的帮助申请）
        long helpProCount = helpApplicationRepository.countByHelperIdAndStatus(userId, "approved");
        dto.setHelpProCount((int) helpProCount);
    }

    // ============================================================
    // 房间信息格式化（与 HelpService 相同的模式）
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
            return typeLabel.isEmpty() ? roomPart : roomPart + "(" + typeLabel + ")";
        }
        String name = user.getName() != null ? user.getName() : "未知用户";
        return typeLabel.isEmpty() ? name : name + "(" + typeLabel + ")";
    }

    private String getUserTypeLabel(String userType) {
        if (userType == null) return "";
        return switch (userType) {
            case "owner" -> "业主";
            case "tenant" -> "租客";
            case "admin" -> "管理员";
            case "super_admin" -> "超级管理员";
            default -> userType != null ? userType : "";
        };
    }
}
