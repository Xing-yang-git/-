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

    /** 按小区统计指定状态的闲置物品数 */
    long countByTenantIdAndStatus(Long tenantId, String status);

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

    /**
     * 语义搜索候选集查询 — 拉取同小区、指定类型、在架且有 embedding 的物品，
     * 供 SemanticSearchService 通过 pgvector 原生查询做向量检索。
     */
    @Query("SELECT i FROM IdleItem i WHERE i.tenantId = :tenantId " +
           "AND i.postType = :postType AND i.status = :status " +
           "AND i.embedding IS NOT NULL")
    List<IdleItem> findCandidatesForSearch(
            @Param("tenantId") Long tenantId,
            @Param("postType") String postType,
            @Param("status") String status);

    /**
     * 向量相似度匹配 — 使用 pgvector 余弦距离查找与目标向量最相似的闲置物品。
     *
     * <p>用于供需匹配场景：发布 WANTED 时，查找历史 LEND 中语义相似的物品。</p>
     *
     * <p>返回 {@code Object[]} 数组，每行元素：
     * <ul>
     *   <li>[0] — 物品 ID（Long）</li>
     *   <li>[1] — 物品标题（String）</li>
     *   <li>[2] — 发布者用户 ID（Long）</li>
     *   <li>[3] — 余弦距离（Double），越小越相似</li>
     * </ul></p>
     */
    @Query(value = "SELECT i.id, i.title, i.user_id, " +
           "CAST(i.embedding AS vector) <=> CAST(:embedding AS vector) AS distance " +
           "FROM idle_items i " +
           "WHERE i.tenant_id = :tenantId " +
           "  AND i.post_type = :postType " +
           "  AND i.user_id != :excludeUserId " +
           "  AND i.status IN (:statuses) " +
           "  AND i.updated_at > :since " +
           "  AND i.embedding IS NOT NULL " +
           "  AND i.embedding <=> CAST(:embedding AS vector) < :threshold " +
           "ORDER BY distance ASC " +
           "LIMIT :limit", nativeQuery = true)
    List<Object[]> findSimilarByEmbedding(
            @Param("embedding") String embedding,
            @Param("tenantId") Long tenantId,
            @Param("postType") String postType,
            @Param("excludeUserId") Long excludeUserId,
            @Param("statuses") List<String> statuses,
            @Param("since") LocalDateTime since,
            @Param("threshold") double threshold,
            @Param("limit") int limit);
}
