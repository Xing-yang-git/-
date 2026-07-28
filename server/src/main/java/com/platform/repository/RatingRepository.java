package com.platform.repository;

import com.platform.model.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {

    List<Rating> findByToUserId(Long toUserId);

    Optional<Rating> findFirstByBorrowIdAndFromUserId(Long borrowId, Long fromUserId);

    Optional<Rating> findFirstByHelpApplicationIdAndFromUserId(Long helpApplicationId, Long fromUserId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.toUserId = :userId")
    Double getAverageScore(@Param("userId") Long userId);

    long countByToUserId(Long userId);

    /** 批量查询多个借用记录的所有评价 */
    List<Rating> findByBorrowIdIn(List<Long> borrowIds);

    /** 批量查询多个帮助申请的所有评价 */
    List<Rating> findByHelpApplicationIdIn(List<Long> helpAppIds);
}
