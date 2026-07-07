package com.platform.service;

import com.platform.model.dto.RatingDTO;
import com.platform.model.dto.RatingRequest;
import com.platform.model.entity.*;
import com.platform.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RatingService 单元测试")
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private BorrowRequestRepository borrowRequestRepository;
    @Mock
    private HelpApplicationRepository helpApplicationRepository;
    @Mock
    private HelpRequestRepository helpRequestRepository;
    @Mock
    private IdleItemRepository idleItemRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RatingService ratingService;

    private UUID fromUserId;
    private UUID ownerId;
    private UUID borrowId;
    private UUID helpApplicationId;
    private UUID idleId;
    private UUID helpId;

    @BeforeEach
    void setUp() {
        fromUserId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        borrowId = UUID.randomUUID();
        helpApplicationId = UUID.randomUUID();
        idleId = UUID.randomUUID();
        helpId = UUID.randomUUID();
    }

    // ==================== submitRating - borrow ====================

    @Test
    @DisplayName("提交借入评价 - 正常评价成功")
    void should_submitBorrowRating_when_validBorrow() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setBorrowId(borrowId);
        req.setScore(5);

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(borrowId)
                .idleId(idleId)
                .borrowerId(fromUserId)
                .status("returned")
                .build();

        IdleItem idleItem = IdleItem.builder()
                .id(idleId)
                .userId(ownerId)
                .build();

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.of(borrowRequest));
        when(ratingRepository.findByBorrowIdAndFromUserId(borrowId, fromUserId)).thenReturn(Optional.empty());
        when(idleItemRepository.findById(idleId)).thenReturn(Optional.of(idleItem));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Map<String, Object> result = ratingService.submitRating(fromUserId, req);

        // Assert
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("message", "评价成功");
        verify(ratingRepository).save(any(Rating.class));
    }

    @Test
    @DisplayName("提交借入评价 - 借入记录不存在时抛出异常")
    void should_throwException_when_borrowNotFound() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setBorrowId(borrowId);
        req.setScore(5);

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("借入记录不存在");
    }

    @Test
    @DisplayName("提交借入评价 - 借入未归还时抛出异常")
    void should_throwException_when_borrowNotReturned() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setBorrowId(borrowId);
        req.setScore(5);

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(borrowId)
                .borrowerId(fromUserId)
                .status("approved")
                .build();

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.of(borrowRequest));

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("只能对已归还的借入记录进行评价");
    }

    @Test
    @DisplayName("提交借入评价 - 非借入方评价时抛出异常")
    void should_throwException_when_notBorrower() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setBorrowId(borrowId);
        req.setScore(5);

        UUID otherUserId = UUID.randomUUID();
        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(borrowId)
                .borrowerId(otherUserId)
                .status("returned")
                .build();

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.of(borrowRequest));

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("只有借入方可以进行评价");
    }

    @Test
    @DisplayName("提交借入评价 - 重复评价时抛出异常")
    void should_throwException_when_alreadyRatedBorrow() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setBorrowId(borrowId);
        req.setScore(5);

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(borrowId)
                .idleId(idleId)
                .borrowerId(fromUserId)
                .status("returned")
                .build();

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.of(borrowRequest));
        when(ratingRepository.findByBorrowIdAndFromUserId(borrowId, fromUserId))
                .thenReturn(Optional.of(new Rating()));

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("您已经评价过该借入记录");
    }

    @Test
    @DisplayName("提交借入评价 - 通过targetId和ratingType别名提交")
    void should_submitRating_when_usingTargetIdAlias() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setTargetId(borrowId);
        req.setRatingType("borrow");
        req.setOverallScore(4);

        BorrowRequest borrowRequest = BorrowRequest.builder()
                .id(borrowId)
                .idleId(idleId)
                .borrowerId(fromUserId)
                .status("returned")
                .build();

        IdleItem idleItem = IdleItem.builder()
                .id(idleId)
                .userId(ownerId)
                .build();

        when(borrowRequestRepository.findById(borrowId)).thenReturn(Optional.of(borrowRequest));
        when(ratingRepository.findByBorrowIdAndFromUserId(borrowId, fromUserId)).thenReturn(Optional.empty());
        when(idleItemRepository.findById(idleId)).thenReturn(Optional.of(idleItem));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Map<String, Object> result = ratingService.submitRating(fromUserId, req);

        // Assert
        assertThat(result).containsEntry("success", true);
        assertThat(req.getScore()).isEqualTo(4);
    }

    // ==================== submitRating - help ====================

    @Test
    @DisplayName("提交帮助评价 - 正常评价成功")
    void should_submitHelpRating_when_validHelp() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setHelpApplicationId(helpApplicationId);
        req.setScore(5);

        HelpApplication application = HelpApplication.builder()
                .id(helpApplicationId)
                .helpId(helpId)
                .helperId(fromUserId)
                .status("completed")
                .build();

        HelpRequest helpRequest = HelpRequest.builder()
                .id(helpId)
                .userId(ownerId)
                .build();

        when(helpApplicationRepository.findById(helpApplicationId)).thenReturn(Optional.of(application));
        when(ratingRepository.findByHelpApplicationIdAndFromUserId(helpApplicationId, fromUserId))
                .thenReturn(Optional.empty());
        when(helpRequestRepository.findById(helpId)).thenReturn(Optional.of(helpRequest));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Map<String, Object> result = ratingService.submitRating(fromUserId, req);

        // Assert
        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("message", "评价成功");
    }

    @Test
    @DisplayName("提交帮助评价 - 帮助申请不存在时抛出异常")
    void should_throwException_when_helpApplicationNotFound() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setHelpApplicationId(helpApplicationId);
        req.setScore(5);

        when(helpApplicationRepository.findById(helpApplicationId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("帮助申请不存在");
    }

    @Test
    @DisplayName("提交帮助评价 - 帮助未完成时抛出异常")
    void should_throwException_when_helpNotCompleted() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setHelpApplicationId(helpApplicationId);
        req.setScore(5);

        HelpApplication application = HelpApplication.builder()
                .id(helpApplicationId)
                .helperId(fromUserId)
                .status("approved")
                .build();

        when(helpApplicationRepository.findById(helpApplicationId)).thenReturn(Optional.of(application));

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("只能对已完成的帮助进行评价");
    }

    @Test
    @DisplayName("提交帮助评价 - 非帮助方评价时抛出异常")
    void should_throwException_when_notHelper() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setHelpApplicationId(helpApplicationId);
        req.setScore(5);

        UUID otherUserId = UUID.randomUUID();
        HelpApplication application = HelpApplication.builder()
                .id(helpApplicationId)
                .helperId(otherUserId)
                .status("completed")
                .build();

        when(helpApplicationRepository.findById(helpApplicationId)).thenReturn(Optional.of(application));

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("只有帮助方可以进行评价");
    }

    @Test
    @DisplayName("提交评价 - 既没有borrowId也没有helpApplicationId时抛出异常")
    void should_throwException_when_noTargetSpecified() {
        // Arrange
        RatingRequest req = new RatingRequest();
        req.setScore(5);

        // Act & Assert
        assertThatThrownBy(() -> ratingService.submitRating(fromUserId, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("请指定要评价的借入记录或帮助申请");
    }

    // ==================== getUserRatings ====================

    @Test
    @DisplayName("获取用户评分 - 正常返回评分列表和平均分")
    void should_returnUserRatings_when_userHasRatings() {
        // Arrange
        Rating rating1 = Rating.builder()
                .id(UUID.randomUUID())
                .fromUserId(UUID.randomUUID())
                .toUserId(ownerId)
                .score(5)
                .createdAt(LocalDateTime.now())
                .build();
        Rating rating2 = Rating.builder()
                .id(UUID.randomUUID())
                .fromUserId(UUID.randomUUID())
                .toUserId(ownerId)
                .score(4)
                .createdAt(LocalDateTime.now())
                .build();

        when(ratingRepository.findByToUserId(ownerId)).thenReturn(List.of(rating1, rating2));
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(
                User.builder().name("评分者").build()));

        // Act
        Map<String, Object> result = ratingService.getUserRatings(ownerId);

        // Assert
        assertThat(result.get("ratings")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<RatingDTO> ratings = (List<RatingDTO>) result.get("ratings");
        assertThat(ratings).hasSize(2);
        assertThat((Double) result.get("averageScore")).isEqualTo(4.5);
        assertThat((Long) result.get("totalRatings")).isEqualTo(2L);
    }

    @Test
    @DisplayName("获取用户评分 - 用户没有评分时返回空列表和0分")
    void should_returnEmptyRatings_when_userHasNoRatings() {
        // Arrange
        when(ratingRepository.findByToUserId(ownerId)).thenReturn(Collections.emptyList());

        // Act
        Map<String, Object> result = ratingService.getUserRatings(ownerId);

        // Assert
        @SuppressWarnings("unchecked")
        List<RatingDTO> ratings = (List<RatingDTO>) result.get("ratings");
        assertThat(ratings).isEmpty();
        assertThat((Double) result.get("averageScore")).isEqualTo(0.0);
        assertThat((Long) result.get("totalRatings")).isEqualTo(0L);
    }
}
