package com.platform.ai.agent;

import com.platform.common.AgentConversationStatus;
import com.platform.common.AgentMessageRole;
import com.platform.common.BizException;
import com.platform.model.entity.AgentConversation;
import com.platform.model.entity.AgentMessage;
import com.platform.model.entity.User;
import com.platform.repository.AgentConversationRepository;
import com.platform.repository.AgentMessageRepository;
import com.platform.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent 会话归档服务 — PG 长期记忆（Redis 热会话 → 归档 → 恢复/软删）。
 *
 * <p>归档触发三合一：消息数达阈值（archive-message-count）/ 空闲超时（调度器扫描）/ 主动结束。
 * 归档后 Redis 热会话保留到 TTL 自然过期（支持"继续上次"冷启动）。</p>
 */
@Slf4j
@Service
public class ArchiveService {

    private final SessionService sessionService;
    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserRepository userRepository;

    /** 恢复会话时回填的最近轮数 */
    @Value("${ai.agent.max-turns:10}")
    private int resumeTurns;

    public ArchiveService(SessionService sessionService,
                          AgentConversationRepository conversationRepository,
                          AgentMessageRepository messageRepository,
                          UserRepository userRepository) {
        this.sessionService = sessionService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /**
     * 归档 Redis 热会话到 PG（无会话或无消息时跳过）。
     *
     * <p>修复要点：
     * <ul>
     *   <li>未绑定小区（tenantId 为 null）跳过归档，避免违反 NOT NULL 约束</li>
     *   <li>复用既有 conversationId（追加消息），避免重复会话行</li>
     *   <li>归档后清空 Redis 热会话 messages，防止调度器每轮重复归档（死循环）</li>
     * </ul>
     *
     * @param userId 住户用户 ID
     */
    @Transactional
    public void archive(Long userId) {
        AgentSession session = sessionService.getSession(userId);
        if (session == null || session.getMessages().isEmpty()) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        Long tenantId = user != null ? user.getTenantId() : null;
        if (tenantId == null) {
            log.warn("会话归档跳过（用户未绑定小区，避免 NOT NULL 冲突）: userId={}", userId);
            return;
        }

        // 复用既有归档会话（conversationId 回填后存在）或新建
        AgentConversation conversation = null;
        if (session.getConversationId() != null) {
            conversation = conversationRepository.findById(session.getConversationId()).orElse(null);
        }
        if (conversation == null || !AgentConversationStatus.ARCHIVED.equals(conversation.getStatus())) {
            conversation = AgentConversation.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .title(buildTitle(session))
                    .messageCount(0)
                    .status(AgentConversationStatus.ARCHIVED)
                    .lastMessageAt(LocalDateTime.now())
                    .build();
            conversation = conversationRepository.save(conversation);
        }

        // 批量追加消息（saveAll 替代 N+1 逐条 save）
        List<AgentMessage> messages = new ArrayList<>();
        for (AgentSession.AgentMessageItem item : session.getMessages()) {
            messages.add(AgentMessage.builder()
                    .conversationId(conversation.getId())
                    .role(item.role())
                    .content(item.content())
                    .sources(item.sources())
                    .actions(item.actions())
                    .build());
        }
        messageRepository.saveAll(messages);

        conversation.setMessageCount(conversation.getMessageCount() + messages.size());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // 归档后清空热会话 messages（保留 conversationId 供冷启动），避免调度器重复归档
        session.setConversationId(conversation.getId());
        session.setMessages(new ArrayList<>());
        session.setLastActive(LocalDateTime.now());
        sessionService.saveSession(userId, session);
        log.info("Agent 会话归档完成: userId={}, conversationId={}, 追加 {} 条",
                userId, conversation.getId(), messages.size());
    }

    /**
     * 历史会话列表（排除软删，分页）。
     *
     * @param userId   住户用户 ID
     * @param pageable 分页参数
     * @return 历史会话分页（id/title/messageCount/status/updatedAt）
     */
    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(Long userId, Pageable pageable) {
        Page<AgentConversation> page = conversationRepository
                .findByUserIdAndStatusNot(userId, AgentConversationStatus.DELETED, pageable);
        return page.map(c -> Map.<String, Object>of(
                "id", c.getId(),
                "title", c.getTitle() == null ? "未命名对话" : c.getTitle(),
                "messageCount", c.getMessageCount() == null ? 0 : c.getMessageCount(),
                "status", c.getStatus(),
                "updatedAt", c.getUpdatedAt() == null ? c.getCreatedAt() : c.getUpdatedAt()));
    }

    /**
     * 恢复归档会话到 Redis 热会话（回填最近 N 轮），并清空原热会话。
     *
     * @param userId         住户用户 ID
     * @param conversationId 归档会话 ID
     */
    @Transactional
    public void resume(Long userId, Long conversationId) {
        // 先归档当前未归档热会话，避免被下方 new AgentSession() 覆盖导致消息丢失
        archive(userId);

        AgentConversation conversation = conversationRepository
                .findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BizException("会话不存在或无权访问"));
        if (AgentConversationStatus.DELETED.equals(conversation.getStatus())) {
            throw new BizException("会话已删除");
        }

        // 读归档消息 → 回填最近 resumeTurns×2 条到 Redis 热会话
        List<AgentMessage> messages = messageRepository.findByConversationIdOrderByIdAsc(conversationId);
        List<AgentSession.AgentMessageItem> items = messages.stream()
                .map(m -> new AgentSession.AgentMessageItem(m.getRole(), m.getContent(), m.getSources(), m.getActions()))
                .collect(Collectors.toList());
        int start = Math.max(0, items.size() - resumeTurns * 2);
        List<AgentSession.AgentMessageItem> recent = new ArrayList<>(items.subList(start, items.size()));

        AgentSession session = new AgentSession();
        // 清空 conversationId：恢复后继续对话再归档时新建会话（分支），
        // 避免复用原会话把已归档消息再次 saveAll 造成重复入库
        session.setConversationId(null);
        session.setMessages(recent);
        session.setLastActive(LocalDateTime.now());
        sessionService.saveSession(userId, session);
        log.info("Agent 会话恢复: userId={}, conversationId={}, 回填 {} 条", userId, conversationId, recent.size());
    }

    /**
     * 软删会话（批量），保留审计；越权/不存在的 id 静默跳过。
     *
     * @param userId          住户用户 ID
     * @param conversationIds 会话 ID 列表
     * @return 实际删除的会话数
     */
    @Transactional
    public int softDelete(Long userId, List<Long> conversationIds) {
        AtomicInteger count = new AtomicInteger();
        for (Long id : conversationIds) {
            conversationRepository.findByIdAndUserId(id, userId).ifPresent(c -> {
                c.setStatus(AgentConversationStatus.DELETED);
                conversationRepository.save(c);
                count.incrementAndGet();
            });
        }
        log.info("Agent 会话软删: userId={}, 会话 {} 个", userId, count.get());
        return count.get();
    }

    /**
     * 由首条 user 消息生成会话标题（截取前 20 字）。
     *
     * @param session 热会话
     * @return 标题
     */
    private String buildTitle(AgentSession session) {
        String firstUser = session.getMessages().stream()
                .filter(m -> AgentMessageRole.USER.equals(m.role()))
                .map(AgentSession.AgentMessageItem::content)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("小邻对话");
        return firstUser.length() > 20 ? firstUser.substring(0, 20) : firstUser;
    }
}
