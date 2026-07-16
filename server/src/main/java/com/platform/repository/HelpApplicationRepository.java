package com.platform.repository;

import com.platform.model.entity.HelpApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpApplicationRepository extends JpaRepository<HelpApplication, Long> {

    List<HelpApplication> findByHelperId(Long helperId);

    List<HelpApplication> findByHelpIdInAndStatus(List<Long> helpIds, String status);

    List<HelpApplication> findByHelpId(Long helpId);

    List<HelpApplication> findByHelpIdAndStatus(Long helpId, String status);

    List<HelpApplication> findByStatus(String status);

    long countByHelperIdAndStatus(Long helperId, String status);

    // 同一 helper 对同一求助是否已有进行中的申请（防重复提交）
    boolean existsByHelpIdAndHelperIdAndStatusIn(Long helpId, Long helperId, List<String> statuses);
}
