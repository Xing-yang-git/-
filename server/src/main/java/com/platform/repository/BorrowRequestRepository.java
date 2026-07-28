package com.platform.repository;

import com.platform.model.entity.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, Long> {

    List<BorrowRequest> findByBorrowerId(Long borrowerId);

    List<BorrowRequest> findByIdleIdInAndStatus(List<Long> idleIds, String status);

    List<BorrowRequest> findByIdleId(Long idleId);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<BorrowRequest> findByStatus(String status);

    List<BorrowRequest> findByBorrowerIdAndStatus(Long borrowerId, String status);

    /** 检查指定用户对指定闲置物品是否存在给定状态的借入申请 */
    boolean existsByBorrowerIdAndIdleIdAndStatus(Long borrowerId, Long idleId, String status);

    @Query("SELECT br FROM BorrowRequest br WHERE br.idleItem.userId = :ownerId AND br.status = :status")
    List<BorrowRequest> findByOwnerIdAndStatus(@Param("ownerId") Long ownerId, @Param("status") String status);

    /** 按小区统计指定状态的借入申请数（JOIN 闲置物品表） */
    @Query("SELECT COUNT(br) FROM BorrowRequest br JOIN br.idleItem ii WHERE br.status = :status AND ii.tenantId = :tenantId")
    long countByStatusAndTenantId(@Param("status") String status, @Param("tenantId") Long tenantId);

    /** 全局统计指定状态的借入申请数（super_admin 用，不限小区） */
    long countByStatus(String status);
}
