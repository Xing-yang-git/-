package com.platform.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.common.Result;
import com.platform.model.dto.AgentChatRequest;
import com.platform.model.dto.AgentStreamEvent;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Agent「小邻」对话 REST API（C端，仅登录即可访问）。
 *
 * <p>提供：
 * <ul>
 *   <li>POST /chat — 流式对话（SSE 伪流式：决策轮非流式生成全文，后端分块播放）</li>
 *   <li>GET /suggestions — 快捷问题（常量，后续可 B端配置）</li>
 *   <li>GET /history — 历史会话列表（分页，排除软删）</li>
 *   <li>POST /history/{id}/resume — 恢复归档会话（回填最近 N 轮，先归档当前热会话防丢失）</li>
 *   <li>DELETE /history — 批量软删历史会话（保留审计）</li>
 * </ul>
 *
 * <p>安全：内存限流防刷 API 额度；SSE error 事件只透出白名单业务文案，内部细节仅记日志。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final ArchiveService archiveService;
    private final ObjectMapper objectMapper;
    private final RateLimitService rateLimitService;
    private final ThreadPoolTaskExecutor agentExecutor;

    public AgentController(AgentService agentService,
                           ArchiveService archiveService,
                           ObjectMapper objectMapper,
                           RateLimitService rateLimitService,
                           ThreadPoolTaskExecutor agentExecutor) {
        this.agentService = agentService;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.agentExecutor = agentExecutor;
    }

    /**
     * 流式对话 — SSE 事件流（限流 429 时返回普通 JSON 错误）。
     *
     * @param req  用户消息
     * @param auth 当前认证用户
     * @return SSE 流（start → answer 分块 → sources → end / error）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody AgentChatRequest req, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());

        // 限流：每分钟每用户配额，超限直接 429（普通 JSON，不开启 SSE）
        if (!rateLimitService.tryAcquire(String.valueOf(userId))) {
            SseEmitter reject = new SseEmitter();
            try {
                reject.send(SseEmitter.event().name("error")
                        .data(toJson("error", "发送太频繁，请稍后再试")));
            } catch (IOException e) {
                log.debug("限流响应发送失败: userId={}", userId);
            }
            reject.complete();
            return reject;
        }

        SseEmitter emitter = new SseEmitter(300_000L);
        // 客户端断开 / 超时：确保 emitter 完成，避免后续 send 抛异常污染日志
        emitter.onTimeout(emitter::complete);
        emitter.onCompletion(() -> log.debug("Agent SSE 流结束: userId={}", userId));

        agentExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("start")
                        .data(toJson("start", Map.of("message", req.getMessage()))));

                // Phase A：RAG 检索 + 生成全文（阻塞，一次性完成）
                AgentChatResult result = agentService.chat(userId, req.getMessage());

                // 伪流式：全文分块播放（模拟逐字输出）
                for (String chunk : chunk(result.reply(), 8)) {
                    emitter.send(SseEmitter.event().name("answer").data(toJson("answer", chunk)));
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 引用来源（后端检索结果，非模型输出）
                emitter.send(SseEmitter.event().name("sources").data(toJson("sources", result.sources())));

                // 动作卡片（写操作，需用户确认；前端渲染确认卡片后跳发布页预填）
                if (result.actions() != null && !result.actions().isEmpty()) {
                    emitter.send(SseEmitter.event().name("action").data(toJson("action", result.actions())));
                }

                emitter.send(SseEmitter.event().name("end").data(toJson("end", Map.of("done", true))));
                emitter.complete();
            } catch (Exception e) {
                // 安全映射：只透出白名单业务文案，内部细节（SQL/上游错误）仅记日志
                String safeMessage = "服务开小差了，请稍后重试";
                if (e instanceof AiGenerationException || e instanceof BizException) {
                    safeMessage = e.getMessage();
                }
                log.error("Agent 对话失败: userId={}", userId, e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(toJson("error", safeMessage)));
                } catch (IOException ignored) {
                    // 连接已断开，忽略
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 历史会话列表（排除软删，分页）。
     *
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @param auth 当前认证用户
     * @return 历史会话分页（id/title/messageCount/status/updatedAt）
     */
    @GetMapping("/history")
    public Result<?> history(@RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size,
                             Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        // size 钳制上限，防超大分页拖垮查询
        size = Math.min(size, 50);
        size = Math.max(size, 1);
        Page<Map<String, Object>> result = archiveService.list(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        return Result.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "currentPage", result.getNumber(),
                "size", result.getSize()));
    }

    /**
     * 恢复归档会话到热会话（回填最近 N 轮，供继续对话）。
     *
     * @param id   归档会话 ID
     * @param auth 当前认证用户
     * @return 恢复结果
     */
    @PostMapping("/history/{id}/resume")
    public Result<?> resume(@PathVariable Long id, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        archiveService.resume(userId, id);
        return Result.ok(Map.of("conversationId", id, "resumed", true));
    }

    /**
     * 批量软删历史会话（保留审计）。
     *
     * @param body 请求体：{"ids":[1,2,3]}
     * @param auth 当前认证用户
     * @return 实际删除的会话数
     */
    @DeleteMapping("/history")
    public Result<?> deleteHistory(@RequestBody(required = false) Map<String, Object> body, Authentication auth) {
        Long userId = Long.valueOf(auth.getName());
        // 安全解析 ids：兼容 Number / 数字字符串，非数组/空 body 不抛异常
        List<Long> ids = new ArrayList<>();
        if (body == null) {
            return Result.error(400, "ids 不能为空");
        }
        Object raw = body.get("ids");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                } else if (o instanceof String s) {
                    try {
                        ids.add(Long.valueOf(s));
                    } catch (NumberFormatException ignored) {
                        // 非法 id 跳过
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            return Result.error(400, "ids 不能为空");
        }
        int deleted = archiveService.softDelete(userId, ids);
        return Result.ok(Map.of("deleted", deleted));
    }

    /**
     * 快捷问题列表（对话页输入框上方 chips）。
     *
     * @return 快捷问题数组
     */
    @GetMapping("/suggestions")
    public Result<?> suggestions() {
        return Result.ok(List.of(
                "物业几点下班？",
                "帮我搜电钻",
                "我借了啥该还了",
                "怎么发布求助",
                "帮我写发布文案"
        ));
    }

    /**
     * 将事件序列化为 SSE data JSON。
     *
     * @param type 事件类型
     * @param data 事件数据
     * @return JSON 字符串
     * @throws JsonProcessingException 序列化失败
     */
    private String toJson(String type, Object data) throws JsonProcessingException {
        return objectMapper.writeValueAsString(new AgentStreamEvent(type, data));
    }

    /**
     * 将文本按固定长度切块（伪流式播放用）。
     *
     * @param text 完整文本
     * @param size 每块字符数
     * @return 分块列表
     */
    private List<String> chunk(String text, int size) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return chunks;
    }
}
