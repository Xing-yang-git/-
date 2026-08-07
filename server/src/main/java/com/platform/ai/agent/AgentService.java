package com.platform.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.ai.search.KnowledgeHit;
import com.platform.ai.search.KnowledgeRetrievalService;
import com.platform.common.AgentMessageRole;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.User;
import com.platform.repository.TenantRepository;
import com.platform.repository.UserRepository;
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

/**
 * AI Agent「小邻」对话编排服务。
 *
 * <p>Phase B（Step 4）：读工具原生 tool calling（Spring AI 自动执行，工具内认证注入）；
 * 写操作走 IntentRouter 解析模型返回的 JSON 意图，生成动作卡片（不自动落库，前端确认后走既有 API）。</p>
 */
@Slf4j
@Service
public class AgentService {

    private final KnowledgeRetrievalService retrievalService;
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

    public AgentService(KnowledgeRetrievalService retrievalService,
                        AgentPromptBuilder promptBuilder,
                        AgentToolDispatcher toolDispatcher,
                        IntentRouter intentRouter,
                        SessionService sessionService,
                        ArchiveService archiveService,
                        OpenAiChatModel deepseekChatModel,
                        UserRepository userRepository,
                        TenantRepository tenantRepository,
                        ObjectMapper objectMapper,
                        MessagePreFilter preFilter) {
        this.retrievalService = retrievalService;
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
    }

    /**
     * 处理一次对话（阻塞版）：消息前置过滤 → RAG 检索 → Prompt 组装（含读工具）→ 生成回复 → 意图解析。
     *
     * <p>供非流式调用方与单元测试使用；线上流式走 {@link #chatStream}。
     * 被前置过滤器拦截的消息直接返回本地文案，不调 RAG/LLM、不写入会话历史。</p>
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

        // 消息前置过滤器：低质消息直接本地应答，不调 RAG/LLM、不写会话历史
        MessagePreFilter.PreFilterResult filter = preFilter.process(userId, message);
        if (filter.blockReply() != null) {
            if (filter.clearSession()) {
                sessionService.clearSession(userId);
            }
            log.info("Agent 消息被前置过滤器拦截: userId={}, clearSession={}, reply={}",
                    userId, filter.clearSession(), filter.blockReply());
            return new AgentChatResult(filter.blockReply(), List.of(), List.of());
        }

        PreparedChat prepared = prepare(userId, filter.message(), filter.injectionHint());

        // deepseek 调用（Spring AI 自动执行读工具循环）
        ChatResponse response = deepseekChatModel.call(prepared.prompt());
        String reply = response.getResult().getOutput().getText();
        if (reply == null || reply.isBlank()) {
            throw new AiGenerationException("AI 回复为空，请稍后重试");
        }

        AgentAction action = intentRouter.parse(reply);
        String displayReply = cleanReply(reply, action);

        // 写入热会话（user + assistant），并触发消息数阈值归档
        List<AgentAction> actions = action != null ? List.of(action) : List.of();
        sessionService.append(userId, AgentMessageRole.USER, message, null, null);
        sessionService.append(userId, AgentMessageRole.ASSISTANT, displayReply,
                writeJsonOrNull(prepared.hits()), writeJsonOrNull(actions));
        archiveIfNeeded(userId);

        return new AgentChatResult(displayReply, prepared.hits(), actions);
    }

    /**
     * 流式对话入口：问候/前置过滤器拦截走快速通道秒回；否则完成 RAG/历史/Prompt 组装后返回模型流。
     *
     * <p>流式的意图解析、会话落库由 Controller 在流结束后回调
     * {@link #completeStream} 完成（因为需要累计到完整回复才能解析 JSON 意图）。</p>
     *
     * @param userId  当前用户 ID
     * @param message 用户消息
     * @return 对话流（问候为 {@code isGreeting()}、拦截为 {@code isBlocked()} 快捷回复，普通为模型内容流）
     */
    public AgentChatStream chatStream(Long userId, String message) {
        // 问候语快速通道：纯寒暄不调任何外部 API（RAG/LLM），秒回保证 3~5s 目标
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

        // 放行：用清洗后的消息继续原有链路（注入特征提示仅附加进模型输入）
        PreparedChat prepared = prepare(userId, filter.message(), filter.injectionHint());
        // Spring AI 真流式：工具调用在流内自动执行（流式工具循环），内容分块直出
        Flux<ChatResponse> contentFlux = deepseekChatModel.stream(prepared.prompt());
        return new AgentChatStream(null, null, prepared.hits(), contentFlux, userId, message);
    }

    /**
     * 流式结束后回填会话并触发归档（由 Controller 在累计完整回复后调用）。
     *
     * @param userId         当前用户 ID
     * @param userMessage    用户消息
     * @param assistantReply 展示用回复（已剔除意图 JSON）
     * @param hits           RAG 引用来源
     * @param action         写操作意图（可为 null）
     */
    public void completeStream(Long userId, String userMessage, String assistantReply,
                               List<KnowledgeHit> hits, AgentAction action) {
        List<AgentAction> actions = action != null ? List.of(action) : List.of();
        sessionService.append(userId, AgentMessageRole.USER, userMessage, null, null);
        sessionService.append(userId, AgentMessageRole.ASSISTANT, assistantReply,
                writeJsonOrNull(hits), writeJsonOrNull(actions));
        archiveIfNeeded(userId);
    }

    /**
     * RAG 检索 + 历史 + Prompt 组装（含读工具注册）。
     *
     * @param userId        当前用户 ID
     * @param message       清洗后的用户消息
     * @param injectionHint 注入特征提示（非空时附加进送入模型的用户消息，检索与历史保持原文）
     * @return 检索命中 + 组装好的 Prompt
     */
    private PreparedChat prepare(Long userId, String message, String injectionHint) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        Long tenantId = user.getTenantId();
        String tenantName = resolveTenantName(tenantId);

        // RAG 检索（向量优先，关键词兜底）
        List<KnowledgeHit> hits = retrievalService.search(tenantId, message);

        // 读取多轮历史（Redis 热会话，Redis 不可用降级为空）
        List<AgentSession.AgentMessageItem> history = sessionService.getHistory(userId);

        // 注入特征提示：仅附加进本次请求送入模型的用户消息（与用户内容用空行明确分隔）
        String modelUserMessage = injectionHint == null
                ? message
                : message + "\n\n（系统提示：" + injectionHint + "）";

        // 组装 Prompt：消息（system + 历史 + user）+ 读工具注册 + 参数
        List<Message> messages = promptBuilder.buildMessages(tenantName, modelUserMessage, hits, history);
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .temperature(0.2)
                // reasoning 模型思维链占用大，1024 预留足够空间
                .maxTokens(1024)
                .toolCallbacks(buildToolCallbacks(userId))
                .build());
        return new PreparedChat(hits, prompt);
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

    /** 流式对话的结果载体：问候/拦截快捷回复 或 模型内容流 + 元数据 */
    public record AgentChatStream(String greetingReply, String blockReply, List<KnowledgeHit> sources,
                                  Flux<ChatResponse> contentFlux, Long userId, String userMessage) {
        /** 构造问候快速通道结果（空内容流，不订阅） */
        public static AgentChatStream greeting(String reply) {
            return new AgentChatStream(reply, null, List.of(), Flux.empty(), null, null);
        }

        /** 构造前置过滤器拦截结果（空内容流，不订阅，不写会话历史） */
        public static AgentChatStream blocked(String reply) {
            return new AgentChatStream(null, reply, List.of(), Flux.empty(), null, null);
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

    /** RAG 检索结果 + Prompt 的载体（阻塞/流式共用） */
    private record PreparedChat(List<KnowledgeHit> hits, Prompt prompt) {
    }

    /**
     * 注册读工具（可安全自动执行）— userId 从认证上下文闭包注入，工具签名不含身份参数。
     *
     * @param userId 当前用户 ID
     * @return ToolCallback 数组
     */
    private ToolCallback[] buildToolCallbacks(Long userId) {
        log.info("Agent 读工具注册: userId={}, 模型={}", userId, deepseekChatModel.getClass().getSimpleName());
        return new ToolCallback[]{
                FunctionToolCallback.builder("search_items", (SearchParams p) -> toolDispatcher.searchItems(userId, p))
                        .description("搜索闲置物品，按关键词查小区内的出借/求借物品")
                        .inputType(SearchParams.class)
                        .build(),
                FunctionToolCallback.builder("my_posts", (MyPostsParams p) -> toolDispatcher.myPosts(userId, p))
                        .description("查询我发布的物品列表")
                        .inputType(MyPostsParams.class)
                        .build(),
                FunctionToolCallback.builder("my_borrows_due", (VoidParams v) -> toolDispatcher.myBorrowsDue(userId))
                        .description("查询我进行中的借用")
                        .inputType(VoidParams.class)
                        .build(),
                FunctionToolCallback.builder("generate_feedback", (FeedbackParams p) -> toolDispatcher.generateFeedback(userId, p))
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
