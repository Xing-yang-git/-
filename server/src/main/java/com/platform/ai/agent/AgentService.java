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

import java.util.List;

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
    private final OpenAiChatModel deepseekChatModel;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    /** 消息数归档阈值（达到即触发归档；与 yml 及截断封顶 max-turns×2 一致，防阈值 > 截断上限永不触发） */
    @Value("${ai.agent.archive-message-count:20}")
    private int archiveMessageCount;

    public AgentService(KnowledgeRetrievalService retrievalService,
                        AgentPromptBuilder promptBuilder,
                        AgentToolDispatcher toolDispatcher,
                        IntentRouter intentRouter,
                        SessionService sessionService,
                        ArchiveService archiveService,
                        OpenAiChatModel deepseekChatModel,
                        UserRepository userRepository,
                        TenantRepository tenantRepository,
                        ObjectMapper objectMapper) {
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
    }

    /**
     * 处理一次对话：RAG 检索 → Prompt 组装（含读工具）→ 生成回复 → 意图解析。
     *
     * @param userId  当前用户 ID（从认证上下文取，租户隔离；工具执行时注入，模型不可伪造）
     * @param message 用户消息
     * @return 回复文本 + 引用来源 + 动作卡片
     */
    public AgentChatResult chat(Long userId, String message) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        Long tenantId = user.getTenantId();
        String tenantName = resolveTenantName(tenantId);

        // RAG 检索（向量优先，关键词兜底）
        List<KnowledgeHit> hits = retrievalService.search(tenantId, message);

        // 读取多轮历史（Redis 热会话，Redis 不可用降级为空）
        List<AgentSession.AgentMessageItem> history = sessionService.getHistory(userId);

        // 组装 Prompt：消息（system + 历史 + user）+ 读工具注册 + 参数
        List<Message> messages = promptBuilder.buildMessages(tenantName, message, hits, history);
        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .temperature(0.2)
                // reasoning 模型思维链占用大，1024 预留足够空间
                .maxTokens(1024)
                .toolCallbacks(buildToolCallbacks(userId))
                .build());

        // deepseek 调用（Spring AI 自动执行读工具循环）
        ChatResponse response = deepseekChatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();
        if (reply == null || reply.isBlank()) {
            throw new AiGenerationException("AI 回复为空，请稍后重试");
        }

        // 意图解析：写操作 JSON → 动作卡片（普通回答返回空 actions）
        AgentAction action = intentRouter.parse(reply);
        List<AgentAction> actions = action != null ? List.of(action) : List.of();

        // 命中动作卡片时，原始 JSON 不应逐字播进气泡：剔除 JSON 段，空则替换为友好提示
        String displayReply = reply.trim();
        if (action != null) {
            displayReply = intentRouter.stripJson(displayReply);
            if (displayReply.isBlank()) {
                displayReply = "已为您准备好发布草稿，请点击下方卡片确认填写。";
            }
        }

        // 写入热会话（user + assistant），并触发消息数阈值归档
        sessionService.append(userId, AgentMessageRole.USER, message, null, null);
        sessionService.append(userId, AgentMessageRole.ASSISTANT, displayReply,
                writeJsonOrNull(hits), writeJsonOrNull(actions));
        archiveIfNeeded(userId);

        return new AgentChatResult(displayReply, hits, actions);
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
}
