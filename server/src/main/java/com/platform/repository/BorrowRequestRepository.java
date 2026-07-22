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

    @Query("SELECT br FROM BorrowRequest br WHERE br.idleItem.userId = :ownerId AND br.status = :status")
    List<BorrowRequest> findByOwnerIdAndStatus(@Param("ownerId") Long ownerId, @Param("status") String status);
}
