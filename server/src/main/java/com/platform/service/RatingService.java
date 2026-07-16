package com.platform.service;

import com.platform.model.dto.RatingDTO;
import com.platform.model.dto.RatingRequest;
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
import com.platform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class RatingService {

    private final RatingRepository ratingRepository;
    private final BorrowRequestRepository borrowRequestRepository;
    private final HelpApplicationRepository helpApplicationRepository;
    private final HelpRequestRepository helpRequestRepository;
    private final IdleItemRepository idleItemRepository;
    private final UserRepository userRepository;

    public RatingService(RatingRepository ratingRepository,
                         BorrowRequestRepository borrowRequestRepository,
                         HelpApplicationRepository helpApplicationRepository,
                         HelpRequestRepository helpRequestRepository,
                         IdleItemRepository idleItemRepository,
                         UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.borrowRequestRepository = borrowRequestRepository;
        this.helpApplicationRepository = helpApplicationRepository;
        this.helpRequestRepository = helpRequestRepository;
        this.idleItemRepository = idleItemRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> submitRating(Long fromUserId, RatingRequest req) {
        // 将 C端字段名归一化为后端字段名
        if (req.getBorrowId() == null && req.getHelpApplicationId() == null && req.getTargetId() != null) {
            if ("help".equals(req.getRatingType())) {
                req.setHelpApplicationId(req.getTargetId());
            } else {
                req.setBorrowId(req.getTargetId());
            }
        }
        if (req.getScore() == null && req.getOverallScore() != null) {
            req.setScore(req.getOverallScore());
        }

        if (req.getBorrowId() != null) {
            BorrowRequest borrowRequest = borrowRequestRepository.findById(req.getBorrowId())
                    .orElseThrow(() -> new RuntimeException("借入记录不存在"));

            if (!"returned".equals(borrowRequest.getStatus())) {
                throw new RuntimeException("只能对已归还的借入记录进行评价");
            }

            IdleItem idleItem = idleItemRepository.findById(borrowRequest.getIdleId()).orElse(null);
            Long ownerId = idleItem != null ? idleItem.getUserId() : null;
            Long borrowerId = borrowRequest.getBorrowerId();

            // 借入双方（借入方 / 物品所有者）任一方均可评价对方
            boolean isBorrower = borrowerId.equals(fromUserId);
            boolean isOwner = ownerId != null && ownerId.equals(fromUserId);
            if (!isBorrower && !isOwner) {
                throw new RuntimeException("无权评价该借入记录");
            }
            Long toUserId = isBorrower ? ownerId : borrowerId;
            // 物品被删除时 ownerId 为 null——to_user_id 是 NOT NULL 列，
            // 在业务层给出明确报错，而不是落库时抛数据库约束异常
            if (toUserId == null) {
                throw new RuntimeException("对方用户信息缺失，无法评价");
            }

            boolean alreadyRated = ratingRepository
                    .findByBorrowIdAndFromUserId(req.getBorrowId(), fromUserId).isPresent();
            if (alreadyRated) {
                throw new RuntimeException("您已经评价过该借入记录");
            }

            Rating rating = new Rating();
            rating.setFromUserId(fromUserId);
            rating.setToUserId(toUserId);
            rating.setBorrowId(req.getBorrowId());
            rating.setScore(req.getScore());
            rating.setCreatedAt(LocalDateTime.now());
            ratingRepository.save(rating);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "评价成功");
            return result;
        }

        if (req.getHelpApplicationId() != null) {
            HelpApplication application = helpApplicationRepository.findById(req.getHelpApplicationId())
                    .orElseThrow(() -> new RuntimeException("帮助申请不存在"));

            if (!"completed".equals(application.getStatus())) {
                throw new RuntimeException("只能对已完成的帮助进行评价");
            }

            HelpRequest helpRequest = helpRequestRepository.findById(application.getHelpId()).orElse(null);
            Long requesterId = helpRequest != null ? helpRequest.getUserId() : null;
            Long helperId = application.getHelperId();

            // 互助双方（帮助方 / 求助方）任一方均可评价对方
            boolean isHelper = helperId.equals(fromUserId);
            boolean isRequester = requesterId != null && requesterId.equals(fromUserId);
            if (!isHelper && !isRequester) {
                throw new RuntimeException("无权评价该帮助");
            }
            Long toUserId = isHelper ? requesterId : helperId;
            // 求助被删除时 requesterId 为 null——同上，业务层防御 NOT NULL 约束
            if (toUserId == null) {
                throw new RuntimeException("对方用户信息缺失，无法评价");
            }

            boolean alreadyRated = ratingRepository
                    .findByHelpApplicationIdAndFromUserId(req.getHelpApplicationId(), fromUserId).isPresent();
            if (alreadyRated) {
                throw new RuntimeException("您已经评价过该帮助");
            }

            Rating rating = new Rating();
            rating.setFromUserId(fromUserId);
            rating.setToUserId(toUserId);
            rating.setHelpApplicationId(req.getHelpApplicationId());
            rating.setScore(req.getScore());
            rating.setCreatedAt(LocalDateTime.now());
            ratingRepository.save(rating);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "评价成功");
            return result;
        }

        throw new RuntimeException("请指定要评价的借入记录或帮助申请");
    }

    public Map<String, Object> getUserRatings(Long userId) {
        List<Rating> ratings = ratingRepository.findByToUserId(userId);
        List<RatingDTO> ratingDTOs = ratings.stream().map(this::toDTO).collect(Collectors.toList());

        double averageScore = ratings.stream()
                .mapToInt(Rating::getScore)
                .average()
                .orElse(0.0);

        Map<String, Object> result = new HashMap<>();
        result.put("ratings", ratingDTOs);
        result.put("averageScore", Math.round(averageScore * 10.0) / 10.0);
        result.put("totalRatings", (long) ratings.size());
        return result;
    }

    private RatingDTO toDTO(Rating rating) {
        User fromUser = userRepository.findById(rating.getFromUserId()).orElse(null);

        return RatingDTO.builder()
                .id(rating.getId())
                .fromUserName(fromUser != null ? fromUser.getName() : "未知用户")
                .score(rating.getScore())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}
