package com.platform.ai.document;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档处理进程内 in-flight 锁 — 同一文档禁止并行解析/重试/删除，防止重复切片。
 *
 * <p>单实例部署用进程内集合即可；多实例时需换 Redis 分布式锁（v2）。</p>
 */
@Component
public class DocumentProcessGuard {

    /** 正在处理中的文档 ID 集合 */
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * 尝试占用某文档的处理权。
     *
     * @param docId 文档 ID
     * @return true 表示获得处理权（此前未在处理）；false 表示已有任务在处理中
     */
    public boolean tryAcquire(Long docId) {
        return inFlight.add(docId);
    }

    /**
     * 释放文档处理权（处理结束或失败时调用，幂等）。
     *
     * @param docId 文档 ID
     */
    public void release(Long docId) {
        inFlight.remove(docId);
    }
}
