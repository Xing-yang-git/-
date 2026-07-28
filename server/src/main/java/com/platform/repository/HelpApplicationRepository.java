package com.platform.repository;

import com.platform.model.entity.HelpApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 检查指定用户对指定求助是否存在给定状态的帮助申请 */
    boolean existsByHelperIdAndHelpIdAndStatus(Long helperId, Long helpId, String status);

    /** 按小区统计指定状态的帮助申请数（JOIN 互助请求表） */
    @Query("SELECT COUNT(ha) FROM HelpApplication ha JOIN ha.helpRequest hr WHERE ha.status = :status AND hr.tenantId = :tenantId")
    long countByStatusAndTenantId(@Param("status") String status, @Param("tenantId") Long tenantId);

    /** 全局统计指定状态的帮助申请数（super_admin 用，不限小区） */
    long countByStatus(String status);
}
