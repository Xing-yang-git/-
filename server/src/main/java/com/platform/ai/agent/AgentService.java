package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.search.KnowledgeHit;
import com.platform.common.AgentMessageRole;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.User;
import com.platform.repository.TenantRepository;
import com.platform.repository.UserRepository;
import com.platform.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent「小邻」对话编排服务。
 *
 * <p>知识检索工具化（Step 3b）：去掉对非问候消息的无条件知识检索，检索改由模型按需调用
 * {@code search_knowledge} 工具（requestId 贯穿工具计数与命中缓存生命周期）；新增 4 个常用业务工具
 * （查日期/查通知/查互助/我的待办）。回复经防幻觉校验：移除非法 [N] 引用、检测资料外数字（仅记日志）。</p>
 *
 * <p>输出敏感词替换（Step 4c）：展示文本在发给前端前经敏感词掩码（命中词替换为 {@code ***}），
 * 写入会话历史保持原文，避免掩码污染后续上下文判断。关键链路日志（Step 4d）：拦截命中、LLM 耗时、
 * 路由决策（本次请求是否调用了知识工具）。</p>
 *
 * <p>写操作走 IntentRouter 解析模型返回的 JSON 意图，生成动作卡片（不自动落库，前端确认后走既有 API）。</p>
 */
@Slf4j
@Service
public class AgentService {

    private final AgentPromptBuilder promptBuilder;
    private final AgentToolDispatcher toolDispatcher;
    private final IntentRouter intentRouter;
    private final SessionService sessionService;
    private final ArchiveService archiveService;
    private final MessagePreFilter preFilter;
    private final OpenAiChatModel deepseekChatModel;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final SensitiveWordService sensitiveWordService;

    /** 消息数归档阈值（达到即触发归档；与 yml 及截断封顶 max-turns×2 一致，防阈值 > 截断上限永不触发） */
    @Value("${ai.agent.archive-message-count:20}")
    private int archiveMessageCount;

    /** 问候语关键词（纯寒暄走快速通道，不调外部 API，保证秒回；31 词完整清单，去重后无重复项） */
    private static final List<String> GREETING_KEYWORDS = List.of(
            "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "在不在",
            "早上好", "中午好", "下午好", "晚上好", "早安", "午安", "晚安",
            "嘿嘿", "哈哈", "哈喽哈", "嗨嗨", "在嘛", "在不在呀", "有人吗", "有人在吗",
            "打扰一下", "请问在吗", "早", "新年好", "节日快乐", "周末好", "好久不见", "好久没见");

    /** 问候语快速回复文案 */
    private static final String GREETING_REPLY =
            "你好呀！我是小邻，小区里的智能助手，可以帮你查物业服务、搜闲置物品、了解平台使用。有什么想问的吗？";

    // ==================== 防幻觉常量 ====================

    /** 引用标记 [N]（N 为数字），用于来源引用校验 */
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d+\\]");

    /** 资料型数字/电话模式：连字符号码、连续 ≥5 位数字、400 段、1xx 短码 */
    private static final Pattern DATA_NUMBER_PATTERN = Pattern.compile(
            "(?<![\\d-])\\d{1,4}(?:[- ]\\d{1,4}){1,2}[- ]\\d{3,}(?![\\d-])" +
                    "|(?<!\\d)\\d{5,}(?!\\d)" +
                    "|(?<![\\d-])400\\d{0,3}(?![\\d-])" +
                    "|(?<![\\d-])1\\d{2}(?![\\d-])");

    /** 相对量词起始字符（数字后紧跟这些中文量词视为相对表达，如「2天后」「3小时后」，不校验） */
    private static final String RELATIVE_TIME_CHARS = "天小时分秒钟";

    public AgentService(AgentPromptBuilder promptBuilder,
                        AgentToolDispatcher toolDispatcher,
                        IntentRouter intentRouter,
                        SessionService sessionService,
                        ArchiveService archiveService,
                        OpenAiChatModel deepseekChatModel,
                        UserRepository userRepository,
                        TenantRepository tenantRepository,
                        ObjectMapper objectMapper,
                        MessagePreFilter preFilter,
                        SensitiveWordService sensitiveWordService) {
        this.promptBuilder = promptBuilder;
        this.toolDispatcher = toolDispatcher;
        this.intentRouter = intentRouter;
        this.sessionService = sessionService;
        this.archiveService = archiveService;
        this.deepseekChatModel = deepseekChatModel;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
        this.preFilter = preFilter;
        this.sensitiveWordService = sensitiveWordService;
    }

    /**
     * 处理一次对话（阻塞版）：消息前置过滤 → Prompt 组装（含读工具）→ 生成回复 → 意图解析 → 防幻觉校验。
     *
     * <p>供非流式调用方与单元测试使用；线上流式走 {@link #chatStream}。
     * 被前置过滤器拦截的消息直接返回本地文案，不调 LLM、不写入会话历史。</p>
     *
     * @param userId  当前用户 ID（从认证上下文取，租户隔离；工具执行时注入，模型不可伪造）
     * @param message 用户消息
     * @return 回复文本 + 引用来源 + 动作卡片
     */
    public AgentChatResult chat(Long userId, String message) {
        String greetingReply = greetingReply(message);
        if (greetingReply != null) {
            sessionService.append(userId, AgentMessageRole.USER, message, null, null);
            sessionService.append(userId, AgentMessageRole.ASSISTANT, greetingReply, null, null);
            return new AgentChatResult(greetingReply, List.of(), List.of());
        }

        // 消息前置过滤器：低质消息直接本地应答，不调 LLM、不写会话历史
        MessagePreFilter.PreFilterResult filter = preFilter.process(userId, message);
        if (filter.blockReply() != null) {
            if (filter.clearSession()) {
                sessionService.clearSession(userId);
            }
            log.info("Agent 消息被前置过滤器拦截: userId={}, clearSession={}, reply={}",
                    userId, filter.clearSession(), filter.blockReply());
            return new AgentChatResult(filter.blockReply(), List.of(), List.of());
        }

        // 请求级上下文：requestId 贯穿工具计数与命中缓存生命周期
        String requestId = UUID.randomUUID().toString();
        toolDispatcher.reset(requestId);
        try {
            Prompt prompt = prepare(userId, filter.message(), filter.injectionHint(), requestId);

            // deepseek 调用（Spring AI 自动执行读工具循环）；非流式首字延迟 ≈ 总耗时，故仅记录总耗时
            long llmStartMs = System.currentTimeMillis();
            ChatResponse response = deepseekChatModel.call(prompt);
            log.info("Agent LLM 调用耗时（非流式）: userId={}, 总耗时={}ms",
                    userId, System.currentTimeMillis() - llmStartMs);
            String reply = response.getResult().getOutput().getText();
            if (reply == null || reply.isBlank()) {
                throw new AiGenerationException("AI 回复为空，请稍后重试");
            }

            AgentAction action = intentRouter.parse(reply);
            String clean = cleanReply(reply, action);
            // 防幻觉：取回本次工具命中（引用来源），移除非法 [N]、检测资料外数字（仅记日志）
            List<KnowledgeHit> sources = toolDispatcher.takeHits(requestId);
            String guarded = applyHallucinationGuard(userId, clean, sources);
            // 输出敏感词替换：只影响展示文本；历史保留 guarded 原文，避免掩码污染后续上下文判断
            String display = maskForDisplay(guarded);
            // 路由决策日志：本次请求是否调用了知识工具（以命中缓存是否有结果为判断依据）
            log.info("Agent 路由决策: userId={}, 本次调用了知识工具={}", userId, !sources.isEmpty());

            // 写入热会话（user + assistant），并触发消息数阈值归档
            List<AgentAction> actions = action != null ? List.of(action) : List.of();
            sessionService.append(userId, AgentMessageRole.USER, message, null, null);
            sessionService.append(userId, AgentMessageRole.ASSISTANT, guarded,
                    writeJsonOrNull(sources), writeJsonOrNull(actions));
            archiveIfNeeded(userId);

            return new AgentChatResult(display, sources, actions);
        } finally {
            // 无论成功或异常都清理请求级工具状态（计数 + 命中缓存）
            toolDispatcher.reset(requestId);
        }
    }

    /**
     * 流式对话入口：问候/前置过滤器拦截走快速通道秒回；否则完成 Prompt 组装后返回模型流。
     *
     * <p>流式的意图解析、会话落库由 Controller 在流结束后回调
     * {@link #completeStream} 完成（因为需要累计到完整回复才能解析 JSON 意图）。
     * 引用来源在流结束时由 Controller 从 {@link AgentToolDispatcher#takeHits} 取回。</p>
     *
     * @param userId  当前用户 ID
     * @param message 用户消息
     * @return 对话流（问候为 {@code isGreeting()}、拦截为 {@code isBlocked()} 快捷回复，普通为模型内容流）
     */
    public AgentChatStream chatStream(Long userId, String message) {
        // 问候语快速通道：纯寒暄不调任何外部 API（LLM），秒回保证 3~5s 目标
        String greetingReply = greetingReply(message);
        if (greetingReply != null) {
            sessionService.append(userId, AgentMessageRole.USER, message, null, null);
            sessionService.append(userId, AgentMessageRole.ASSISTANT, greetingReply, null, null);
            return AgentChatStream.greeting(greetingReply);
        }

        // 消息前置过滤器：拦截（空消息/纯符号/控制指令/纯表情/重复/超长/敏感词）直接本地应答
        MessagePreFilter.PreFilterResult filter = preFilter.process(userId, message);
        if (filter.blockReply() != null) {
            if (filter.clearSession()) {
                sessionService.clearSession(userId);
            }
            log.info("Agent 消息被前置过滤器拦截: userId={}, clearSession={}, reply={}",
                    userId, filter.clearSession(), filter.blockReply());
            return AgentChatStream.blocked(filter.blockReply());
        }

        // 请求级上下文：requestId 贯穿工具计数与命中缓存生命周期（流结束时取回并清理）
        String requestId = UUID.randomUUID().toString();
        toolDispatcher.reset(requestId);

        // 放行：用清洗后的消息继续原有链路（注入特征提示仅附加进模型输入）
        Prompt prompt = prepare(userId, filter.message(), filter.injectionHint(), requestId);
        // Spring AI 真流式：工具调用在流内自动执行（流式工具循环），内容分块直出
        Flux<ChatResponse> contentFlux = deepseekChatModel.stream(prompt);
        return new AgentChatStream(null, null, contentFlux, userId, message, requestId);
    }

    /**
     * 流式结束后回填会话并触发归档（由 Controller 在累计完整回复后调用）。
     *
     * @param userId         当前用户 ID
     * @param userMessage    用户消息
     * @param assistantReply 写入历史的助手回复（已剔除意图 JSON、经防幻觉引用校验，但未做敏感词掩码——历史保留原文）
     * @param hits           引用来源（requestId 缓存取回的工具命中）
     * @param action         写操作意图（可为 null）
     * @param requestId      请求 ID（用于清理工具计数与命中缓存）
     */
    public void completeStream(Long userId, String userMessage, String assistantReply,
                               List<KnowledgeHit> hits, AgentAction action, String requestId) {
        List<AgentAction> actions = action != null ? List.of(action) : List.of();
        sessionService.append(userId, AgentMessageRole.USER, userMessage, null, null);
        sessionService.append(userId, AgentMessageRole.ASSISTANT, assistantReply,
                writeJsonOrNull(hits), writeJsonOrNull(actions));
        archiveIfNeeded(userId);
        // 路由决策日志：本次请求是否调用了知识工具（以命中缓存是否有结果为判断依据）
        log.info("Agent 路由决策: userId={}, 本次调用了知识工具={}", userId, hits != null && !hits.isEmpty());
        // 请求级工具状态清理（计数 + 命中缓存）
        toolDispatcher.reset(requestId);
    }

    /**
     * Prompt 组装（含读工具注册）。
     *
     * @param userId        当前用户 ID
     * @param message       清洗后的用户消息
     * @param injectionHint 注入特征提示（非空时附加进送入模型的用户消息，历史保持原文）
     * @param requestId     请求 ID（随工具闭包传入，供计数与命中缓存使用）
     * @return 组装好的 Prompt
     */
    private Prompt prepare(Long userId, String message, String injectionHint, String requestId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        String tenantName = resolveTenantName(user.getTenantId());

        // 读取多轮历史（Redis 热会话，Redis 不可用降级为空）
        List<AgentSession.AgentMessageItem> history = sessionService.getHistory(userId);

        // 注入特征提示：仅附加进本次请求送入模型的用户消息（与用户内容用空行明确分隔）
        String modelUserMessage = injectionHint == null
                ? message
                : message + "\n\n（系统提示：" + injectionHint + "）";

        // 组装 Prompt：消息（system + 历史 + user）+ 读工具注册 + 参数（知识检索由模型按需调工具，不再无条件注入）
        List<Message> messages = promptBuilder.buildMessages(tenantName, modelUserMessage, history);
        return new Prompt(messages, OpenAiChatOptions.builder()
                .temperature(0.2)
                // reasoning 模型思维链占用大，1024 预留足够空间
                .maxTokens(1024)
                .toolCallbacks(buildToolCallbacks(userId, requestId))
                .build());
    }

    /**
     * 剔除意图 JSON 后的展示回复（空则替换为友好提示）。
     *
     * <p>流式 Controller 在累计完整回复后也调用此方法计算最终展示文案。</p>
     *
     * @param reply  模型原始回复
     * @param action 解析出的写操作意图（可为 null）
     * @return 气泡展示文本
     */
    public String cleanReply(String reply, AgentAction action) {
        String displayReply = reply == null ? "" : reply.trim();
        if (action != null) {
            displayReply = intentRouter.stripJson(displayReply);
            if (displayReply.isBlank()) {
                displayReply = "已为您准备好发布草稿，请点击下方卡片确认填写。";
            }
        }
        return displayReply;
    }

    /**
     * 对展示文本做敏感词掩码（命中词替换为 {@code ***}）。
     *
     * <p>只影响发给前端的展示文本；写入会话历史的内容保持原文（不调用本方法），
     * 避免掩码污染后续多轮对话的上下文判断。流式 Controller 对每个分块与最终展示文本均调用。</p>
     *
     * @param text 展示文本
     * @return 掩码后的文本（无命中时原样返回）
     */
    public String maskForDisplay(String text) {
        return sensitiveWordService.replace(text);
    }

    /**
     * 防幻觉校验：先移除超出注入资料条数范围的非法 [N] 引用，再检测资料中不存在的数字（仅记日志）。
     *
     * <p>调用方保证传入的 {@code reply} 已是 cleanReply 处理后的文本（剔除 JSON 意图），
     * 数字校验输入即引用校验后的文本。</p>
     *
     * @param userId  当前用户 ID（日志上下文）
     * @param reply   cleanReply 后的回复文本
     * @param sources 本次请求工具命中列表（引用条数范围 = 命中条数；无命中则任何 [N] 都非法）
     * @return 移除非法引用后的文本（数字校验只记日志不改文本）
     */
    public String applyHallucinationGuard(Long userId, String reply, List<KnowledgeHit> sources) {
        if (reply == null) {
            return "";
        }
        String text = reply.trim();
        int hitCount = sources == null ? 0 : sources.size();
        // 1) 来源引用校验：N 超出条数范围 → 移除该非法引用并记日志
        text = stripInvalidCitations(userId, text, hitCount);
        // 2) 关键数字校验：与注入资料对比，资料中不存在的数字记幻觉信号日志（不修改文本）
        checkNumbersAgainstSources(userId, text, sources);
        return text;
    }

    /**
     * 移除回复中超出注入资料条数范围的 [N] 引用（无命中时任何 [N] 都非法）。
     *
     * @param userId   当前用户 ID（日志上下文）
     * @param text     校验前文本
     * @param hitCount 本次注入资料条数
     * @return 移除非法引用后的文本
     */
    private String stripInvalidCitations(Long userId, String text, int hitCount) {
        if (text.isEmpty()) {
            return text;
        }
        Matcher matcher = CITATION_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean modified = false;
        while (matcher.find()) {
            String token = matcher.group();
            int n = Integer.parseInt(token.substring(1, token.length() - 1));
            if (n < 1 || n > hitCount) {
                log.warn("Agent 回复含非法引用已移除: userId={}, 引用编号={}, 注入资料条数={}",
                        userId, n, hitCount);
                modified = true;
                matcher.appendReplacement(sb, "");
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
        }
        matcher.appendTail(sb);
        return modified ? sb.toString() : text;
    }

    /**
     * 提取回复中的资料型数字/电话模式，与本次注入资料文本对比，资料中不存在的数字记幻觉信号日志。
     *
     * <p>保守处理：只记日志、不修改回复文本。相对表达（如「2天后」「3小时后」）不校验。</p>
     *
     * @param userId  当前用户 ID（日志上下文）
     * @param text    引用校验后的文本
     * @param sources 本次注入资料（content/title/source 拼接作为比对语料）
     */
    private void checkNumbersAgainstSources(Long userId, String text, List<KnowledgeHit> sources) {
        StringBuilder corpus = new StringBuilder();
        if (sources != null) {
            for (KnowledgeHit hit : sources) {
                if (hit.content() != null) {
                    corpus.append(hit.content()).append(' ');
                }
                if (hit.title() != null) {
                    corpus.append(hit.title()).append(' ');
                }
                if (hit.source() != null) {
                    corpus.append(hit.source()).append(' ');
                }
            }
        }
        // 与资料语料做同样的归一化（去空白与连字符），保证「400-168-6688」与「4001686688」可比
        String searchableCorpus = corpus.toString().replaceAll("[\\s-]", "");
        Matcher matcher = DATA_NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            // 相对量词排除：数字后紧跟 天/小/时/分/秒/钟 等中文量词（如「2天后」）不校验
            if (matcher.end() < text.length()
                    && RELATIVE_TIME_CHARS.indexOf(text.charAt(matcher.end())) >= 0) {
                continue;
            }
            String digits = matcher.group().replaceAll("[\\s-]", "");
            if (searchableCorpus.indexOf(digits) < 0) {
                log.warn("Agent 回复含资料中不存在的数字（疑似幻觉信号）: userId={}, 数字={}",
                        userId, matcher.group());
            }
        }
    }

    /** 流式对话的结果载体：问候/拦截快捷回复 或 模型内容流 + 元数据 */
    public record AgentChatStream(String greetingReply, String blockReply,
                                  Flux<ChatResponse> contentFlux, Long userId, String userMessage,
                                  String requestId) {
        /** 构造问候快速通道结果（空内容流，不订阅） */
        public static AgentChatStream greeting(String reply) {
            return new AgentChatStream(reply, null, Flux.empty(), null, null, null);
        }

        /** 构造前置过滤器拦截结果（空内容流，不订阅，不写会话历史） */
        public static AgentChatStream blocked(String reply) {
            return new AgentChatStream(null, reply, Flux.empty(), null, null, null);
        }

        /** 是否为问候快速通道（是则直接播 greetingReply） */
        public boolean isGreeting() {
            return greetingReply != null;
        }

        /** 是否为前置过滤器拦截（是则直接播 blockReply） */
        public boolean isBlocked() {
            return blockReply != null;
        }
    }

    /**
     * 注册读工具（可安全自动执行）— userId/requestId 从认证上下文与请求上下文闭包注入，工具签名不含身份参数。
     *
     * @param userId    当前用户 ID
     * @param requestId 请求 ID（工具计数与命中缓存 key）
     * @return ToolCallback 数组
     */
    private ToolCallback[] buildToolCallbacks(Long userId, String requestId) {
        log.info("Agent 读工具注册: userId={}, 模型={}", userId, deepseekChatModel.getClass().getSimpleName());
        return new ToolCallback[]{
                FunctionToolCallback.builder("search_knowledge",
                                (KnowledgeSearchParams p) -> toolDispatcher.searchKnowledge(userId, requestId, p))
                        .description("检索小区知识库（物业服务、规章制度、办事指南、应急联系、平台使用帮助等）。当用户询问小区相关事项、物业规则、办事流程、应急信息时调用。参数为检索关键词，请提取用户问题中的核心词，可做精简改写，不要传整句。")
                        .inputType(KnowledgeSearchParams.class)
                        .build(),
                FunctionToolCallback.builder("query_date",
                                (DateQueryParams p) -> toolDispatcher.queryDate(userId, requestId, p))
                        .description("查询日期和星期，支持今天/明天/昨天/前天/后天/大后天、N天前/N天后、星期几等相对表达。当用户问今天几号、明天星期几、前天是几号时调用")
                        .inputType(DateQueryParams.class)
                        .build(),
                FunctionToolCallback.builder("query_notifications",
                                (VoidParams v) -> toolDispatcher.queryNotifications(userId, requestId, v))
                        .description("查询小区最近的通知/公告。当用户问小区有没有通知、物业发了什么公告时调用")
                        .inputType(VoidParams.class)
                        .build(),
                FunctionToolCallback.builder("query_help_requests",
                                (HelpSearchParams p) -> toolDispatcher.queryHelpRequests(userId, requestId, p))
                        .description("查询小区里的互助求助（找人帮忙、技能求助等）。当用户问小区有没有人需要帮忙、怎么找人帮忙时调用")
                        .inputType(HelpSearchParams.class)
                        .build(),
                FunctionToolCallback.builder("my_todos",
                                (VoidParams v) -> toolDispatcher.myTodos(userId, requestId, v))
                        .description("查询我的待办进度（进行中的借用/互助、待审批的申请）。当用户问我的申请批了没、还有哪些没处理时调用")
                        .inputType(VoidParams.class)
                        .build(),
                FunctionToolCallback.builder("search_items",
                                (SearchParams p) -> toolDispatcher.searchItems(userId, requestId, p))
                        .description("搜索闲置物品，按关键词查小区内的出借/求借物品")
                        .inputType(SearchParams.class)
                        .build(),
                FunctionToolCallback.builder("my_posts",
                                (MyPostsParams p) -> toolDispatcher.myPosts(userId, requestId, p))
                        .description("查询我发布的物品列表")
                        .inputType(MyPostsParams.class)
                        .build(),
                FunctionToolCallback.builder("my_borrows_due",
                                (VoidParams v) -> toolDispatcher.myBorrowsDue(userId, requestId, v))
                        .description("查询我进行中的借用")
                        .inputType(VoidParams.class)
                        .build(),
                FunctionToolCallback.builder("generate_feedback",
                                (FeedbackParams p) -> toolDispatcher.generateFeedback(userId, requestId, p))
                        .description("生成互助感想评价文本")
                        .inputType(FeedbackParams.class)
                        .build()
        };
    }

    /**
     * 触发消息数阈值归档（达到 archive-message-count 即归档到 PG）。
     *
     * @param userId 住户用户 ID
     */
    private void archiveIfNeeded(Long userId) {
        AgentSession session = sessionService.getSession(userId);
        if (session != null && session.getMessages().size() >= archiveMessageCount) {
            archiveService.archive(userId);
        }
    }

    /**
     * 序列化为 JSON 字符串（归档 sources/actions 用），失败返回 null。
     *
     * @param data 数据对象（可为空列表）
     * @return JSON 字符串，或 null
     */
    private String writeJsonOrNull(Object data) {
        try {
            if (data == null) {
                return null;
            }
            String json = objectMapper.writeValueAsString(data);
            return "[]".equals(json) ? null : json;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析小区名称（未绑定小区时兜底为"本小区"）。
     *
     * @param tenantId 小区 ID，可能为 null
     * @return 小区名称
     */
    private String resolveTenantName(Long tenantId) {
        if (tenantId == null) {
            return "本小区";
        }
        return tenantRepository.findById(tenantId).map(Tenant::getName).orElse("本小区");
    }

    /**
     * 判定消息是否为纯问候/寒暄，是则返回快速回复文案（否则返回 null）。
     *
     * <p>判定规则：去空白与标点后，<b>以问候关键词开头</b>，且<b>问候语之后只剩语气词/语气符号</b>
     * （如"你好"、"你好呀"、"早上好"）才算纯寒暄。</p>
     *
     * <p>用 startsWith + 语气词后缀而不是 contains/长度阈值：确保「你好，我想知道物业几点下班」这类
     * 带真实问题的消息<b>一定不会</b>被误判进快速通道——它开头是"你好"但后面跟着实质内容，会正常走 LLM。</p>
     *
     * @param message 用户消息
     * @return 问候回复文案，或 null（非问候）
     */
    private String greetingReply(String message) {
        if (message == null) {
            return null;
        }
        String norm = message.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}。，！？!?～~…·]", "");
        if (norm.isEmpty()) {
            return null;
        }
        for (String keyword : GREETING_KEYWORDS) {
            if (norm.startsWith(keyword)) {
                String rest = norm.substring(keyword.length());
                if (rest.isEmpty() || rest.matches("[呀啊哦啦哈嘛吧喔嗯吗呵]*")) {
                    return GREETING_REPLY;
                }
            }
        }
        return null;
    }
}
