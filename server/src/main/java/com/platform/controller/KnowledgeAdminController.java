package com.platform.controller;

import com.platform.common.BizStatus;
import com.platform.common.Result;
import com.platform.common.UserType;
import com.platform.model.dto.KnowledgeItemDTO;
import com.platform.model.dto.KnowledgeRequest;
import com.platform.model.entity.KnowledgeItem;
import com.platform.model.entity.User;
import com.platform.repository.UserRepository;
import com.platform.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库管理 REST API（B端管理员）。
 *
 * <p>提供 AI Agent「小邻」RAG 知识库条目的 CRUD 与向量管理：
 * <ul>
 *   <li>分页列表（分类/状态/关键词过滤，按管理员所属小区隔离）</li>
 *   <li>创建 / 更新（自动生成 1024 维向量）</li>
 *   <li>软上下架（保留审计）</li>
 *   <li>批量补齐缺失向量</li>
 * </ul>
 *
 * <p>权限由 {@code /api/admin/**} 的 ROLE_ADMIN/ROLE_SUPER_ADMIN 校验控制；
 * super_admin 为平台级视角（tenantId 为 null 查全部），普通 admin 仅限本小区。</p>
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeService knowledgeService;
    private final UserRepository userRepository;

    public KnowledgeAdminController(KnowledgeService knowledgeService, UserRepository userRepository) {
        this.knowledgeService = knowledgeService;
        this.userRepository = userRepository;
    }

    /**
     * 分页查询知识条目。
     *
     * @param page     页码（从 0 开始）
     * @param size     每页条数
     * @param category 分类过滤（可选）
     * @param status   状态过滤（可选）
     * @param keyword  关键词过滤（可选）
     * @param auth     当前认证管理员
     * @return 分页知识条目列表
     */
    @GetMapping
    public Result<?> list(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "10") int size,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String keyword,
                          Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<KnowledgeItem> result = knowledgeService.list(
                resolveTenantId(adminId), category, status, keyword, pageable);
        return Result.ok(Map.of(
                "content", result.getContent().stream().map(this::toDTO).collect(Collectors.toList()),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber(),
                "size", result.getSize()));
    }

    /**
     * 创建知识条目（自动生成 1024 维向量）。
     *
     * @param req  知识条目内容
     * @param auth 当前认证管理员
     * @return 创建成功的条目摘要
     */
    @PostMapping
    public Result<?> create(@Valid @RequestBody KnowledgeRequest req, Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        Long tenantId = resolveTenantId(adminId);
        if (tenantId == null) {
            // super_admin 为平台级，须在请求中指定目标小区
            tenantId = req.getTenantId();
            if (tenantId == null) {
                return Result.error(400, "超级管理员创建知识条目必须指定小区");
            }
        }
        KnowledgeItem item = KnowledgeItem.builder()
                .tenantId(tenantId)
                .category(req.getCategory())
                .title(req.getTitle())
                .content(req.getContent())
                .source(req.getSource())
                .tags(req.getTags())
                .status(req.getStatus() != null ? req.getStatus() : BizStatus.ONLINE)
                .createdBy(adminId)
                .build();
        return Result.ok(toDTO(knowledgeService.create(item)));
    }

    /**
     * 更新知识条目（内容变更后重新生成向量）。
     *
     * @param id  条目 ID
     * @param req 更新内容
     * @param auth 当前认证管理员
     * @return 更新后的条目摘要
     */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id,
                            @Valid @RequestBody KnowledgeRequest req,
                            Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        Long tenantId = resolveTenantId(adminId);
        KnowledgeItem item = knowledgeService.get(id);
        if (item == null) {
            return Result.error(404, "知识条目不存在");
        }
        if (tenantId != null && !tenantId.equals(item.getTenantId())) {
            return Result.error(403, "无权修改其他小区的知识条目");
        }
        item.setCategory(req.getCategory());
        item.setTitle(req.getTitle());
        item.setContent(req.getContent());
        item.setSource(req.getSource());
        item.setTags(req.getTags());
        if (req.getStatus() != null) {
            item.setStatus(req.getStatus());
        }
        return Result.ok(toDTO(knowledgeService.update(item)));
    }

    /**
     * 软上下架知识条目（不物理删除）。
     *
     * @param id     条目 ID
     * @param body   请求体：{"status": "online|offline"}
     * @param auth   当前认证管理员
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    public Result<?> setStatus(@PathVariable Long id,
                               @RequestBody Map<String, String> body,
                               Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        Long tenantId = resolveTenantId(adminId);
        String status = body.get("status");
        if (status == null || (!BizStatus.ONLINE.equals(status) && !BizStatus.OFFLINE.equals(status))) {
            return Result.error(400, "status 必须是 online 或 offline");
        }
        KnowledgeItem item = knowledgeService.get(id);
        if (item == null) {
            return Result.error(404, "知识条目不存在");
        }
        if (tenantId != null && !tenantId.equals(item.getTenantId())) {
            return Result.error(403, "无权修改其他小区的知识条目");
        }
        knowledgeService.setStatus(id, status);
        return Result.ok(Map.of("id", id, "status", status));
    }

    /**
     * 批量补齐缺失向量（管理端 reindex 按钮）。
     *
     * @param auth 当前认证管理员
     * @return 本次补齐的条目数
     */
    @PostMapping("/reindex")
    public Result<?> reindex(Authentication auth) {
        Long adminId = Long.valueOf(auth.getName());
        int count = knowledgeService.reindex(resolveTenantId(adminId));
        return Result.ok(Map.of("count", count));
    }

    /**
     * 解析当前管理员的可管理小区 ID。
     *
     * @param adminId 管理员用户 ID
     * @return super_admin 返回 null（平台级视角），普通 admin 返回自身 tenantId
     */
    private Long resolveTenantId(Long adminId) {
        User admin = userRepository.findById(adminId).orElseThrow(() -> new RuntimeException("管理员不存在"));
        if (UserType.SUPER_ADMIN.equals(admin.getUserType())) {
            return null;
        }
        Long tenantId = admin.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("管理员未关联小区");
        }
        return tenantId;
    }

    /**
     * Entity → DTO（不含 embedding 向量）。
     *
     * @param item 知识条目实体
     * @return 列表响应 DTO
     */
    private KnowledgeItemDTO toDTO(KnowledgeItem item) {
        return KnowledgeItemDTO.builder()
                .id(item.getId())
                .tenantId(item.getTenantId())
                .category(item.getCategory())
                .title(item.getTitle())
                .content(item.getContent())
                .source(item.getSource())
                .tags(item.getTags())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
