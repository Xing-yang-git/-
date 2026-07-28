package com.platform.repository;

import com.platform.model.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 批量查询指定用户的审核通过操作日志，用于获取审核人信息。
     *
     * @param userIds 用户 ID 列表
     * @return 对应用户的 approve_user 操作日志列表
     */
    @Query("SELECT ol FROM OperationLog ol WHERE ol.targetType = 'user' AND ol.targetId IN :userIds AND ol.action = 'approve_user' ORDER BY ol.createdAt DESC")
    List<OperationLog> findApprovalsByUserIds(@Param("userIds") List<Long> userIds);
}
