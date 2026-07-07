package com.platform.repository;

import com.platform.model.entity.HelpRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HelpRequestRepository extends JpaRepository<HelpRequest, UUID> {

    Page<HelpRequest> findByStatus(String status, Pageable pageable);

    @Query("SELECT h FROM HelpRequest h WHERE h.status = :status " +
           "AND (h.title LIKE %:title% OR h.description LIKE %:desc%)")
    Page<HelpRequest> findByStatusAndTitleContainingOrDescriptionContaining(
            @Param("status") String status,
            @Param("title") String title,
            @Param("desc") String desc,
            Pageable pageable);

    List<HelpRequest> findByUserId(UUID userId);

    Page<HelpRequest> findByStatusIn(List<String> statuses, Pageable pageable);

    long countByStatus(String status);

    List<HelpRequest> findByStatus(String status);
}
