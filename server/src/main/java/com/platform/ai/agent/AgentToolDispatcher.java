package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.PolishingClient;
import com.platform.common.BizStatus;
import com.platform.common.PostType;
import com.platform.model.dto.BorrowResponseDTO;
import com.platform.model.dto.IdleItemDTO;
import com.platform.service.BorrowService;
import com.platform.service.IdleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

/**
 * Agent 读工具执行器 — 搜索/查询/生成类工具的真实执行。
 *
 * <p><b>权限边界</b>：工具签名不接受 userId/tenantId 参数，userId 一律由
 * AgentService 从认证上下文注入（模型无法伪造他人身份查询）。</p>
 *
 * <p>仅注册读工具（可安全自动执行）；写操作走 IntentRouter JSON 意图出动作卡片，不在此执行。</p>
 */
@Slf4j
@Component
public class AgentToolDispatcher {

    private final IdleService idleService;
    private final BorrowService borrowService;
    private final PolishingClient polishingClient;
    private final ObjectMapper objectMapper;

    public AgentToolDispatcher(IdleService idleService,
                               BorrowService borrowService,
                               PolishingClient polishingClient,
                               ObjectMapper objectMapper) {
        this.idleService = idleService;
        this.borrowService = borrowService;
        this.polishingClient = polishingClient;
        this.objectMapper = objectMapper;
    }

    /**
     * search_items — 搜索闲置物品（混合检索，租户隔离由 IdleService 保证）。
     *
     * @param userId 当前用户 ID（认证注入）
     * @param p      关键词 + 类型过滤
     * @return 物品摘要 JSON（id/title/description/category）
     */
    public String searchItems(Long userId, SearchParams p) {
        String postType = normalizePostType(p.postType());
        var page = idleService.search(userId, p.keyword(), postType, 0, 5, "hybrid");
        List<Map<String, Object>> items = page.getContent().stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "title", d.getTitle(),
                        "description", d.getDescription() == null ? "" : d.getDescription(),
                        "category", d.getCategory() == null ? "" : d.getCategory()))
                .collect(Collectors.toList());
        log.info("search_items 工具执行: userId={}, keyword={}, postType={}, 返回 {} 条",
                userId, p.keyword(), postType, items.size());
        return writeJson(items);
    }

    /**
     * my_posts — 查询我发布的物品列表。
     *
     * @param userId 当前用户 ID
     * @param p      类型过滤
     * @return 我的发布摘要 JSON
     */
    public String myPosts(Long userId, MyPostsParams p) {
        String postType = normalizePostType(p.postType());
        List<IdleItemDTO> posts = idleService.getMyPosts(userId, postType);
        List<Map<String, Object>> items = posts.stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "title", d.getTitle(),
                        "status", d.getStatus() == null ? "" : d.getStatus()))
                .collect(Collectors.toList());
        return writeJson(items);
    }

    /**
     * my_borrows_due — 查询我进行中的借用（借入方视角）。
     *
     * @param userId 当前用户 ID
     * @return 进行中借用 JSON（物品标题 + 借用时长）
     */
    public String myBorrowsDue(Long userId) {
        List<BorrowResponseDTO> active = borrowService.getMyApplications(userId).stream()
                // 运行时经 approveReject 批准后的借用状态是 approved（种子数据用 active 掩盖了该差异），
                // 与 AdminService 的 ACTIVE || APPROVED 双状态兼容保持一致
                .filter(b -> BizStatus.ACTIVE.equals(b.getStatus()) || BizStatus.APPROVED.equals(b.getStatus()))
                .collect(Collectors.toList());
        List<Map<String, Object>> items = active.stream()
                .map(b -> Map.<String, Object>of(
                        "idleId", b.getIdleId(),
                        "idleTitle", b.getIdleTitle(),
                        "durationDays", b.getDurationDays() == null ? 0 : b.getDurationDays(),
                        "status", b.getStatus()))
                .collect(Collectors.toList());
        return writeJson(items);
    }

    /**
     * generate_feedback — 生成互助感想评价文本（复用 PolishingClient）。
     *
     * @param userId 当前用户 ID
     * @param p      角色 + 标题 + 背景
     * @return 生成的评价文本（JSON 字符串包裹便于模型引用）
     */
    public String generateFeedback(Long userId, FeedbackParams p) {
        String role = (p.role() == null || p.role().isBlank()) ? PolishingClient.ROLE_LEND : p.role();
        String feedback = polishingClient.generateFeedback(role, p.itemTitle(), p.description());
        return writeJson(Map.of("feedback", feedback));
    }

    /** postType 中文别名 → 枚举值映射（模型可能传中文而非枚举，Spring AI record schema 无 enum 约束） */
    private static final Map<String, String> POST_TYPE_ALIAS = new HashMap<>();
    static {
        POST_TYPE_ALIAS.put("求借", PostType.WANTED);
        POST_TYPE_ALIAS.put("需求", PostType.WANTED);
        POST_TYPE_ALIAS.put("借入", PostType.WANTED);
        POST_TYPE_ALIAS.put("想要", PostType.WANTED);
        POST_TYPE_ALIAS.put("求助", PostType.HELP);
        POST_TYPE_ALIAS.put("帮忙", PostType.HELP);
    }

    /**
     * 归一化 postType 参数 — Spring AI 从 record 生成的 schema 无 enum 约束，
     * 模型可能传中文（"出借"/"求借"）而非枚举值，统一映射到 {@link PostType} 合法值。
     *
     * @param raw 模型传入的 postType（可为 null / 中文 / 英文枚举）
     * @return 合法 PostType 值，无法识别默认 LEND
     */
    private String normalizePostType(String raw) {
        if (raw == null || raw.isBlank()) {
            return PostType.LEND;
        }
        String trimmed = raw.trim();
        // 中文别名映射优先（"求借"/"求助"等）
        String mapped = POST_TYPE_ALIAS.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        // 英文枚举透传（WANTED/HELP 合法）；未知值（含"出借"/"借出"/LEND）默认 LEND
        if (PostType.WANTED.equals(trimmed) || PostType.HELP.equals(trimmed)) {
            return trimmed;
        }
        return PostType.LEND;
    }

    /**
     * 序列化为 JSON 字符串（工具结果给模型）。
     *
     * @param data 数据对象
     * @return JSON 字符串，失败返回空 JSON
     */
    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("工具结果序列化失败: {}", e.getMessage());
            return "[]";
        }
    }
}
