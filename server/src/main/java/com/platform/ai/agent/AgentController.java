package com.platform.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.search.KnowledgeHit;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.common.Result;
import com.platform.model.dto.AgentChatRequest;
import com.platform.model.dto.AgentStreamEvent;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
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
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final IntentRouter intentRouter;
    private final AgentToolDispatcher toolDispatcher;

    public AgentController(AgentService agentService,
                           ArchiveService archiveService,
                           ObjectMapper objectMapper,
                           RateLimitService rateLimitService,
                           ThreadPoolTaskExecutor agentExecutor,
                           IntentRouter intentRouter,
                           AgentToolDispatcher toolDispatcher) {
        this.agentService = agentService;
        this.archiveService = archiveService;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
        this.agentExecutor = agentExecutor;
        this.intentRouter = intentRouter;
        this.toolDispatcher = toolDispatcher;
    }

    /**
     * 流式对话 — SSE 事件流（限流 429 时返回普通 JSON 错误）。
     *
     * @param req  用户消息
     * @param auth 当前认证用户
     * @return SSE 流（start → answer 分块 → action/replace → sources → end / error；sources 在流结束后取回发出）
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
                safeSend(emitter, "start", Map.of("message", req.getMessage()));

                // 流式编排：问候秒回；普通对话走 Spring AI 真流式（工具在流内自动执行）
                AgentService.AgentChatStream stream = agentService.chatStream(userId, req.getMessage());

                if (stream.isGreeting()) {
                    // 问候快速通道：直接播固定文案（不订阅内容流）
                    safeSend(emitter, "answer", stream.greetingReply());
                    safeSend(emitter, "end", Map.of("done", true));
                    emitter.complete();
                    return;
                }

                if (stream.isBlocked()) {
                    // 前置过滤器拦截：直接播本地文案（不订阅内容流，不写会话历史）
                    safeSend(emitter, "answer", stream.blockReply());
                    safeSend(emitter, "end", Map.of("done", true));
                    emitter.complete();
                    return;
                }

                // 真流式：模型内容分块直发（引用来源在流结束时取回发出，见 doOnComplete——
                // 工具调用在流内完成，来源仅在流结束可知）。
                // 写操作意图 JSON（模型按要求直接返回 {"intent":...}）会混进内容流——
                // 若首个分块以 { 开头（纯 JSON 意图），暂缓转发，等流结束解析后替换为友好文案，
                // 避免用户看到裸 JSON。
                StringBuilder full = new StringBuilder();
                StringBuilder held = new StringBuilder();
                AtomicBoolean jsonHeldBack = new AtomicBoolean(false);

                stream.contentFlux()
                        .doOnNext(cr -> {
                            String delta = extractText(cr);
                            if (delta == null || delta.isEmpty()) {
                                return;
                            }
                            full.append(delta);
                            if (jsonHeldBack.get()) {
                                held.append(delta);
                            } else if (full.length() == delta.length() && delta.trim().startsWith("{")) {
                                jsonHeldBack.set(true);
                                held.append(delta);
                            } else {
                                safeSend(emitter, "answer", delta);
                            }
                        })
                        .doOnComplete(() -> {
                            String fullText = full.toString();
                            AgentAction action = intentRouter.parse(fullText);
                            String cleanReply = agentService.cleanReply(fullText, action);
                            if (jsonHeldBack.get()) {
                                // 纯 JSON：是意图则替换为友好文案 + 动作卡片；否则原样补发
                                if (action != null) {
                                    safeSend(emitter, "answer", cleanReply);
                                    safeSend(emitter, "action", List.of(action));
                                } else {
                                    safeSend(emitter, "answer", held.toString());
                                }
                            } else if (action != null) {
                                // 文本 + JSON：replace 事件用剔除 JSON 后的文案刷新气泡
                                safeSend(emitter, "replace", cleanReply);
                                safeSend(emitter, "action", List.of(action));
                            }
                            // 运行时取回引用来源（requestId 缓存的工具命中；工具调用在流内完成，流结束才可知）
                            List<KnowledgeHit> sources = toolDispatcher.takeHits(stream.requestId());
                            // 防幻觉：移除非法 [N] 引用、检测资料外数字（仅记日志）
                            String display = agentService.applyHallucinationGuard(userId, cleanReply, sources);
                            // 移除非法引用后文本有变化 → replace 事件刷新前端气泡为校验后文本
                            if (!display.equals(cleanReply)) {
                                safeSend(emitter, "replace", display);
                            }
                            // sources 事件在 answer 分块之后发出（无命中发空列表）
                            safeSend(emitter, "sources", sources);
                            // 回填会话（user + assistant 展示文案，含防幻觉校验结果）并触发归档
                            agentService.completeStream(userId, req.getMessage(), display, sources, action, stream.requestId());
                            safeSend(emitter, "end", Map.of("done", true));
                            emitter.complete();
                        })
                        .doOnError(e -> {
                            log.error("Agent 流式对话失败: userId={}", userId, e);
                            // 流失败同样清理请求级工具状态（计数 + 命中缓存），避免泄漏
                            toolDispatcher.reset(stream.requestId());
                            String safeMessage = toSafeMessage(e);
                            safeSend(emitter, "error", safeMessage);
                            emitter.completeWithError(e);
                        })
                        .subscribe();
            } catch (Exception e) {
                // 安全映射：只透出白名单业务文案，内部细节（SQL/上游错误）仅记日志
                log.error("Agent 对话失败: userId={}", userId, e);
                String safeMessage = toSafeMessage(e);
                safeSend(emitter, "error", safeMessage);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 将异常映射为对用户安全的文案（内部细节仅记日志）。
     *
     * @param e 异常（含响应式流的 Throwable）
     * @return 可透出的业务文案
     */
    private String toSafeMessage(Throwable e) {
        if (e instanceof AiGenerationException || e instanceof BizException) {
            return e.getMessage();
        }
        return "服务开小差了，请稍后重试";
    }

    /**
     * 从流式 ChatResponse 中提取文本增量。
     *
     * @param cr Spring AI 流式响应
     * @return 文本增量，或 null
     */
    private String extractText(ChatResponse cr) {
        if (cr == null || cr.getResult() == null || cr.getResult().getOutput() == null) {
            return null;
        }
        String text = cr.getResult().getOutput().getText();
        return text == null ? null : text;
    }

    /**
     * 发送 SSE 事件（连接已断开时静默忽略，避免污染日志）。
     *
     * @param emitter SSE 发射器
     * @param name    事件名
     * @param data    事件数据（序列化为 AgentStreamEvent JSON）
     */
    private void safeSend(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(toJson(name, data)));
        } catch (IOException e) {
            log.debug("SSE 发送失败（连接可能已断开）: name={}", name);
        }
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
}
