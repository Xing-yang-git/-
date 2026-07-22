package com.platform.repository;

import com.platform.model.entity.IdleItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IdleItemRepository extends JpaRepository<IdleItem, Long> {

    Page<IdleItem> findByStatusAndPostType(String status, String postType, Pageable pageable);

    Page<IdleItem> findByStatusAndPostTypeAndTenantId(String status, String postType, Long tenantId, Pageable pageable);

    Page<IdleItem> findByPostTypeAndStatusIn(String postType, List<String> statuses, Pageable pageable);

    @Query("SELECT i FROM IdleItem i WHERE i.status = :status AND i.postType = :postType " +
           "AND (i.title LIKE %:title% OR i.description LIKE %:desc%)")
    Page<IdleItem> findByStatusAndPostTypeAndTitleContainingOrDescriptionContaining(
            @Param("status") String status,
            @Param("postType") String postType,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    /** 带租户隔离的关键词搜索——搜索必须与 getHomeList 一样限制在本小区内 */
    @Query("SELECT i FROM IdleItem i WHERE i.status = :status AND i.postType = :postType " +
           "AND i.tenantId = :tenantId AND (i.title LIKE %:title% OR i.description LIKE %:desc%)")
    Page<IdleItem> searchByTenant(
            @Param("status") String status,
            @Param("postType") String postType,
            @Param("tenantId") Long tenantId,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    List<IdleItem> findByUserId(Long userId);

    Page<IdleItem> findByUserIdAndPostType(Long userId, String postType, Pageable pageable);

    Page<IdleItem> findByStatusIn(List<String> statuses, Pageable pageable);

    long countByStatus(String status);

    long countByStatusAndPostType(String status, String postType);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<IdleItem> findByStatus(String status);

    /**
     * 带悲观写锁（SELECT ... FOR UPDATE）的按 ID 查询。
     * 用于并发借入申请，确保状态检查 → 修改在同一把锁内原子执行，
     * 防止两个住户同时申请借入同一物品时出现双重借入。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdleItem i WHERE i.id = :id")
    Optional<IdleItem> findByIdWithLock(@Param("id") Long id);
}
