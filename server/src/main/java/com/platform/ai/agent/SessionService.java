package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 热会话服务 — Redis 存储多轮对话上下文（单设备单会话）。
 *
 * <p>Key：{@code agent:session:{userId}}，TTL 续期，写时截断（超轮数丢最旧 + 超字符裁剪）。
 * Redis 不可用时降级为无会话（单轮对话），不阻断主链路。</p>
 */
@Slf4j
@Service
public class SessionService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 会话 TTL（小时） */
    @Value("${ai.agent.session-ttl-hours:24}")
    private int sessionTtlHours;

    /** 保留轮数（消息数上限 = 轮数×2，含 user+assistant） */
    @Value("${ai.agent.max-turns:10}")
    private int maxTurns;

    /** 历史序列化最大字符数 */
    @Value("${ai.agent.max-history-chars:6000}")
    private int maxHistoryChars;

    private static final String SESSION_PREFIX = "agent:session:";

    public SessionService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取热会话（无则返回 null）。
     *
     * @param userId 住户用户 ID
     * @return 热会话，或 null
     */
    public AgentSession getSession(Long userId) {
        try {
            String json = redisTemplate.opsForValue().get(SESSION_PREFIX + userId);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, AgentSession.class);
        } catch (Exception e) {
            log.warn("Redis 会话读取失败（降级无会话）: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 写入热会话并续期 TTL。
     *
     * @param userId  住户用户 ID
     * @param session 热会话
     */
    public void saveSession(Long userId, AgentSession session) {
        try {
            redisTemplate.opsForValue().set(
                    SESSION_PREFIX + userId,
                    objectMapper.writeValueAsString(session),
                    Duration.ofHours(sessionTtlHours));
        } catch (Exception e) {
            log.warn("Redis 会话写入失败（降级无会话）: userId={}, {}", userId, e.getMessage());
        }
    }

    /**
     * 获取会话历史消息（供 AgentService 拼进 LLM 上下文）。
     *
     * @param userId 住户用户 ID
     * @return 历史消息列表（可能为空）
     */
    public List<AgentSession.AgentMessageItem> getHistory(Long userId) {
        AgentSession session = getSession(userId);
        return session != null && session.getMessages() != null ? session.getMessages() : List.of();
    }

    /**
     * 追加一条消息并写回（含截断）。
     *
     * @param userId  住户用户 ID
     * @param role    角色：user/assistant/tool
     * @param content 消息内容
     * @param sources 引用来源 JSON（assistant 消息用，可为空）
     * @param actions 动作卡片 JSON（assistant 消息用，可为空）
     * @return 追加后的会话（Redis 写入失败时内部降级，恒返回非 null）
     */
    public AgentSession append(Long userId, String role, String content, String sources, String actions) {
        AgentSession session = getSession(userId);
        if (session == null) {
            session = new AgentSession();
        }
        session.getMessages().add(new AgentSession.AgentMessageItem(role, content, sources, actions));
        session.setLastActive(LocalDateTime.now());
        AgentSession truncated = truncate(session);
        saveSession(userId, truncated);
        return truncated;
    }

    /**
     * 会话截断：超 max-turns×2 条丢最旧；超 max-history-chars 字符从头部裁剪（至少保留 6 条）。
     *
     * @param session 会话
     * @return 截断后的会话
     */
    private AgentSession truncate(AgentSession session) {
        List<AgentSession.AgentMessageItem> messages = session.getMessages();
        int maxCount = maxTurns * 2;
        if (messages.size() > maxCount) {
            messages = new ArrayList<>(messages.subList(messages.size() - maxCount, messages.size()));
        }
        // 字符裁剪（至少保留 keepMin 条，避免丢失最近上下文）
        int totalChars = messages.stream().mapToInt(m -> len(m.content())).sum();
        int keepMin = 6;
        while (totalChars > maxHistoryChars && messages.size() > keepMin) {
            totalChars -= len(messages.get(0).content());
            messages = new ArrayList<>(messages.subList(1, messages.size()));
        }
        session.setMessages(messages);
        return session;
    }

    private int len(String s) {
        return s == null ? 0 : s.length();
    }
}
