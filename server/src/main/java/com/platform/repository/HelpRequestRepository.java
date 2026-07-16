package com.platform.repository;

import com.platform.model.entity.HelpRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, Long> {

    Page<HelpRequest> findByStatus(String status, Pageable pageable);

    Page<HelpRequest> findByStatusAndTenantId(String status, Long tenantId, Pageable pageable);

    @Query("SELECT h FROM HelpRequest h WHERE h.status = :status " +
           "AND (h.title LIKE %:title% OR h.description LIKE %:desc%)")
    Page<HelpRequest> findByStatusAndTitleContainingOrDescriptionContaining(
            @Param("status") String status,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    /** 带租户隔离的关键词搜索——搜索必须与 getHomeList 一样限制在本小区内 */
    @Query("SELECT h FROM HelpRequest h WHERE h.status = :status AND h.tenantId = :tenantId " +
           "AND (h.title LIKE %:title% OR h.description LIKE %:desc%)")
    Page<HelpRequest> searchByTenant(
            @Param("status") String status,
            @Param("tenantId") Long tenantId,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    List<HelpRequest> findByUserId(Long userId);

    Page<HelpRequest> findByStatusIn(List<String> statuses, Pageable pageable);

    long countByStatus(String status);

    List<HelpRequest> findByStatus(String status);
}
