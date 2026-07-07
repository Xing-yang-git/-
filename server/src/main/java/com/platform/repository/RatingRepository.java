package com.platform.repository;

import com.platform.model.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    List<Rating> findByToUserId(UUID toUserId);

    Optional<Rating> findByBorrowIdAndFromUserId(UUID borrowId, UUID fromUserId);

    Optional<Rating> findByHelpApplicationIdAndFromUserId(UUID helpApplicationId, UUID fromUserId);

    @Query("SELECT AVG(r.score) FROM Rating r WHERE r.toUserId = :userId")
    Double getAverageScore(@Param("userId") UUID userId);

    long countByToUserId(UUID userId);
}
