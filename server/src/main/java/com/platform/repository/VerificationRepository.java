package com.platform.repository;

import com.platform.model.entity.Verification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerificationRepository extends JpaRepository<Verification, UUID> {

    List<Verification> findByUserId(UUID userId);

    List<Verification> findByStatus(String status);
}
