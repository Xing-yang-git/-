package com.platform.repository;

import com.platform.model.entity.IdleItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface IdleItemRepository extends JpaRepository<IdleItem, UUID> {

    Page<IdleItem> findByStatusAndPostType(String status, String postType, Pageable pageable);

    Page<IdleItem> findByPostTypeAndStatusIn(String postType, List<String> statuses, Pageable pageable);

    @Query("SELECT i FROM IdleItem i WHERE i.status = :status AND i.postType = :postType " +
           "AND (i.title LIKE %:title% OR i.description LIKE %:desc%)")
    Page<IdleItem> findByStatusAndPostTypeAndTitleContainingOrDescriptionContaining(
            @Param("status") String status,
            @Param("postType") String postType,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    List<IdleItem> findByUserId(UUID userId);

    Page<IdleItem> findByUserIdAndPostType(UUID userId, String postType, Pageable pageable);

    Page<IdleItem> findByStatusIn(List<String> statuses, Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndPostType(String status, String postType);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<IdleItem> findByStatus(String status);
}
