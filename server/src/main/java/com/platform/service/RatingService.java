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
import java.util.UUID;
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

    public Map<String, Object> submitRating(UUID fromUserId, RatingRequest req) {
        // Normalize C端 field names to backend field names
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
        // Merge C端 dimension fields into dimensionScores
        if (req.getDimensionScores() == null) {
            if (req.getDimScores() != null) {
                req.setDimensionScores(req.getDimScores());
            } else if (req.getDimLabels() != null) {
                req.setDimensionScores(req.getDimLabels());
            }
        }

        if (req.getBorrowId() != null) {
            BorrowRequest borrowRequest = borrowRequestRepository.findById(req.getBorrowId())
                    .orElseThrow(() -> new RuntimeException("借入记录不存在"));

            if (!"returned".equals(borrowRequest.getStatus())) {
                throw new RuntimeException("只能对已归还的借入记录进行评价");
            }

            if (!borrowRequest.getBorrowerId().equals(fromUserId)) {
                throw new RuntimeException("只有借入方可以进行评价");
            }

            boolean alreadyRated = ratingRepository
                    .findByBorrowIdAndFromUserId(req.getBorrowId(), fromUserId).isPresent();
            if (alreadyRated) {
                throw new RuntimeException("您已经评价过该借入记录");
            }

            IdleItem idleItem = idleItemRepository.findById(borrowRequest.getIdleId()).orElse(null);
            UUID ownerId = idleItem != null ? idleItem.getUserId() : null;

            Rating rating = new Rating();
            rating.setFromUserId(fromUserId);
            rating.setToUserId(ownerId);
            rating.setBorrowId(req.getBorrowId());
            rating.setScore(req.getScore());
            rating.setDimensionScores(req.getDimensionScores());
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

            if (!application.getHelperId().equals(fromUserId)) {
                throw new RuntimeException("只有帮助方可以进行评价");
            }

            boolean alreadyRated = ratingRepository
                    .findByHelpApplicationIdAndFromUserId(req.getHelpApplicationId(), fromUserId).isPresent();
            if (alreadyRated) {
                throw new RuntimeException("您已经评价过该帮助");
            }

            HelpRequest helpRequest = helpRequestRepository.findById(application.getHelpId()).orElse(null);
            UUID ownerId = helpRequest != null ? helpRequest.getUserId() : null;

            Rating rating = new Rating();
            rating.setFromUserId(fromUserId);
            rating.setToUserId(ownerId);
            rating.setHelpApplicationId(req.getHelpApplicationId());
            rating.setScore(req.getScore());
            rating.setDimensionScores(req.getDimensionScores());
            rating.setCreatedAt(LocalDateTime.now());
            ratingRepository.save(rating);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "评价成功");
            return result;
        }

        throw new RuntimeException("请指定要评价的借入记录或帮助申请");
    }

    public Map<String, Object> getUserRatings(UUID userId) {
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
                .dimensionScores(rating.getDimensionScores())
                .createdAt(rating.getCreatedAt())
                .build();
    }
}
