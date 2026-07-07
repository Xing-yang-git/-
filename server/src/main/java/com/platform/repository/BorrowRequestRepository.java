package com.platform.repository;

import com.platform.model.entity.BorrowRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BorrowRequestRepository extends JpaRepository<BorrowRequest, UUID> {

    List<BorrowRequest> findByBorrowerId(UUID borrowerId);

    List<BorrowRequest> findByIdleIdInAndStatus(List<UUID> idleIds, String status);

    List<BorrowRequest> findByIdleId(UUID idleId);

    long countByStatusAndCreatedAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<BorrowRequest> findByStatus(String status);

    @Query("SELECT COUNT(br) FROM BorrowRequest br WHERE br.idleItem.userId = :ownerId AND br.status = 'returned'")
    long countReturnedByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("SELECT COUNT(br) FROM BorrowRequest br WHERE br.idleItem.userId = :ownerId AND br.status = 'returned' AND br.isOnTime = true")
    long countOnTimeReturnedByOwnerId(@Param("ownerId") UUID ownerId);

    List<BorrowRequest> findByBorrowerIdAndStatus(UUID borrowerId, String status);

    @Query("SELECT br FROM BorrowRequest br WHERE br.idleItem.userId = :ownerId AND br.status = :status")
    List<BorrowRequest> findByOwnerIdAndStatus(@Param("ownerId") UUID ownerId, @Param("status") String status);
}
