package com.platform.repository;

import com.platform.model.entity.HelpApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HelpApplicationRepository extends JpaRepository<HelpApplication, UUID> {

    List<HelpApplication> findByHelperId(UUID helperId);

    List<HelpApplication> findByHelpIdInAndStatus(List<UUID> helpIds, String status);

    List<HelpApplication> findByHelpId(UUID helpId);

    List<HelpApplication> findByHelpIdAndStatus(UUID helpId, String status);

    List<HelpApplication> findByStatus(String status);

    long countByHelperIdAndStatus(UUID helperId, String status);
}
