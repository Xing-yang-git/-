package com.platform.repository;

import com.platform.model.entity.ExportLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 导出日志数据访问层。
 */
public interface ExportLogRepository extends JpaRepository<ExportLog, Long> {

    /** 按小区ID查询导出日志，按时间倒序返回全部记录 */
    List<ExportLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
