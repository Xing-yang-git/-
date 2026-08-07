package com.platform.repository;

import com.platform.common.SensitiveWordStatus;
import com.platform.model.entity.SensitiveWord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 敏感词数据访问层 — super_admin 管理 CRUD + 启动加载启用词。
 */
public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {

    /**
     * 按状态查询（更新倒序）— 启动加载启用词缓存用。
     *
     * @param status 状态（ENABLED/DISABLED）
     * @return 符合条件的敏感词列表
     */
    List<SensitiveWord> findByStatusOrderByUpdatedAtDesc(SensitiveWordStatus status);

    /**
     * 按状态分页查询（列表用；status 为 null 查全部时由调用方改用 findAll）。
     *
     * @param status   状态（ENABLED/DISABLED）
     * @param pageable 分页参数
     * @return 分页结果
     */
    Page<SensitiveWord> findByStatus(SensitiveWordStatus status, Pageable pageable);

    /**
     * 判断敏感词是否已存在（新增/编辑去重）。
     *
     * @param word 敏感词原文
     * @return true 表示已存在
     */
    boolean existsByWord(String word);
}
