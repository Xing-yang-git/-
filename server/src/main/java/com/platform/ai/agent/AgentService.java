package com.platform.ai.agent;

import com.platform.ai.search.KnowledgeHit;
import com.platform.ai.search.KnowledgeRetrievalService;
import com.platform.common.AiGenerationException;
import com.platform.common.BizException;
import com.platform.model.entity.Tenant;
import com.platform.model.entity.User;
import com.platform.repository.TenantRepository;
import com.platform.repository.UserRepository;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI Agent「小邻」对话编排服务。
 *
 * <p>Phase A（知识问答）：检索知识库 → 组装 Prompt → deepseek-v4-flash 生成回复。
 * Phase B（工具调用）将在 Step 4 扩展为"决策轮 + 工具执行 + 动作卡片"。</p>
 */
@Service
public class AgentService {

    private final KnowledgeRetrievalService retrievalService;
    private final AgentPromptBuilder promptBuilder;
    private final OpenAiChatModel deepseekChatModel;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public AgentService(KnowledgeRetrievalService retrievalService,
                        AgentPromptBuilder promptBuilder,
                        OpenAiChatModel deepseekChatModel,
                        UserRepository userRepository,
                        TenantRepository tenantRepository) {
        this.retrievalService = retrievalService;
        this.promptBuilder = promptBuilder;
        this.deepseekChatModel = deepseekChatModel;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * 处理一次对话：RAG 检索 → Prompt 组装 → 生成回复。
     *
     * @param userId  当前用户 ID（从认证上下文取，租户隔离）
     * @param message 用户消息
     * @return 回复文本 + 引用来源
     */
    public AgentChatResult chat(Long userId, String message) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        Long tenantId = user.getTenantId();
        String tenantName = resolveTenantName(tenantId);

        // Phase A：知识库 RAG 检索（向量优先，关键词兜底）
        List<KnowledgeHit> hits = retrievalService.search(tenantId, message);

        // 组装 Prompt（知识问答场景 temperature 0.2）
        Prompt prompt = promptBuilder.build(tenantName, message, hits);

        // deepseek-v4-flash 非流式生成（reasoning 模型，max_tokens 1024 在 AgentPromptBuilder 指定）
        ChatResponse response = deepseekChatModel.call(prompt);
        String reply = response.getResult().getOutput().getText();
        if (reply == null || reply.isBlank()) {
            throw new AiGenerationException("AI 回复为空，请稍后重试");
        }
        return new AgentChatResult(reply.trim(), hits);
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
