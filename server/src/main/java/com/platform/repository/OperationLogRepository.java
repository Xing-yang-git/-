package com.platform.repository;

import com.platform.model.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OperationLogRepository extends JpaRepository<OperationLog, UUID> {

    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
