package com.platform.ai.matching;

import com.platform.model.entity.IdleItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 供需匹配调度器 — 在后台异步执行匹配逻辑，不阻塞发布请求的响应。
 *
 * <p>使用 {@link CompletableFuture#runAsync(Runnable)} 异步执行，
 * 避免引入 Spring {@code @Async} 配置的额外复杂性。
 * 异步执行中的异常被内部捕获并记录日志，不会传播到主线程。</p>
 */
@Slf4j
@Component
public class MatchingScheduler {

    private final MatchingService matchingService;

    public MatchingScheduler(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    /**
     * 异步触发供需匹配 — 在后台线程中查找历史 LEND 并通知借出方。
     *
     * <p>调用后立即返回，匹配逻辑在 ForkJoinPool 公共线程池中异步执行。
     * 调用方无需等待匹配完成即可返回发布成功的响应。</p>
     *
     * @param wantedItem 刚发布的求借物品
     */
    public void scheduleMatch(IdleItem wantedItem) {
        CompletableFuture.runAsync(() -> {
            try {
                matchingService.matchWantedToLend(wantedItem);
            } catch (Exception e) {
                log.error("异步供需匹配失败: wantedItemId={}, title={}", wantedItem.getId(), wantedItem.getTitle(), e);
            }
        });
    }
}
