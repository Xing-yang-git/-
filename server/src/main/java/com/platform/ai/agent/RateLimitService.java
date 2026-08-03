package com.platform.ai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 对话限流服务 — 防止付费 deepseek/embedding API 被刷爆额度。
 *
 * <p>当前为内存实现（单实例部署可满足）；Step 5 将升级为 Redis INCR 双维度限流（分钟/天），
 * Redis 不可用时降级本内存实现。</p>
 */
@Slf4j
@Service
public class RateLimitService {

    /** 每分钟最大请求数 */
    private final int perMinute;

    /** 每用户每分钟请求时间戳窗口（key = userId） */
    private final Map<String, Deque<Long>> minuteWindows = new ConcurrentHashMap<>();

    public RateLimitService(@Value("${ai.agent.rate-limit-per-minute:10}") int perMinute) {
        this.perMinute = perMinute;
    }

    /**
     * 尝试获取一个请求配额（每分钟窗口滑动计数）。
     *
     * @param userId 用户 ID
     * @return true = 允许，false = 超限
     */
    public boolean tryAcquire(String userId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = minuteWindows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            // 移除 60 秒前的时间戳
            while (!window.isEmpty() && window.peekFirst() < now - 60_000L) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                log.warn("Agent 对话限流触发: userId={}", userId);
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /**
     * 清理指定用户的限流记录（可选，用户注销时调用）。
     *
     * @param userId 用户 ID
     */
    public void clear(String userId) {
        minuteWindows.remove(userId);
    }
}
