package com.platform.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.AiGenerationException;
import com.platform.config.AiConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * AI 文案优化客户端，调用智谱 GLM-4-Flash 进行文本生成与润色。
 *
 * <p>通过 OpenAI 兼容的 /chat/completions 端点发送请求，
 * 当前支持互助感想智能生成（mode=feedback），未来可扩展标题润色等场景。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolishingClient {

    private final RestClient chatRestClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 角色常量：借入方 */
    public static final String ROLE_BORROW = "borrow";
    /** 角色常量：借出方 */
    public static final String ROLE_LEND = "lend";
    /** 角色常量：求助方 */
    public static final String ROLE_HELP_REQ = "helpReq";
    /** 角色常量：帮忙方 */
    public static final String ROLE_HELP_PRO = "helpPro";

    /** 角色中文映射 — 用于 System Prompt 背景 */
    private static final Map<String, String> ROLE_DESC_MAP = Map.of(
            ROLE_BORROW, "我是借入方，邻居把物品借给了我",
            ROLE_LEND, "我是借出方，我把闲置物品借给了邻居",
            ROLE_HELP_REQ, "我是求助方，邻居帮我解决了问题",
            ROLE_HELP_PRO, "我是帮忙方，我帮邻居解决了问题"
    );

    /** 角色对应的 User Message — 强制 AI 理解借贷方向 */
    private static final Map<String, String> ROLE_USER_MSG = Map.of(
            ROLE_BORROW, "我向邻居借了「%s」来用，现在写一段感想。注意：我是借东西进来的人，物品是邻居借给我的。",
            ROLE_LEND, "我把「%s」借给了邻居，现在写一段感想。注意：我是借出东西的人，物品是我的。",
            ROLE_HELP_REQ, "邻居帮我「%s」，现在写一段感想。注意：我是接受帮助的人。",
            ROLE_HELP_PRO, "我帮邻居「%s」，现在写一段感想。注意：我是提供帮助的人。"
    );

    /** 互助感想生成的 System Prompt */
    private static final String FEEDBACK_SYSTEM_PROMPT =
            "你是社区互助平台评价助手。用第一人称\"我\"写一段 20-60 字的互助感想。\n\n" +
            "铁则：\n" +
            "- 只根据补充说明中已提到的事实来写，不要编造任何未提及的事情（包括不要凭空说\"逾期\"\"损坏\"\"磨损\"）\n" +
            "- 补充说明中没提到问题 = 一切顺利，只写正面感受\n" +
            "- 口语化但不要网络用语（如\"超\"\"爆\"），不要以\"邻居\"\"你好\"等称呼开头\n" +
            "- 不用\"唉\"\"哎呀\"\"嘛\"\"呢\"\"啦\"\"呀\"等感叹语气词，不用 emoji\n" +
            "- 不写具体人名房号，不编造信息\n\n" +
            "角色：%s\n" +
            "事项：%s\n" +
            "补充：%s\n\n" +
            "直接返回感想。";

    /**
     * 生成互助感想评价文本。
     *
     * @param role        角色标识：borrow / lend / helpReq / helpPro
     * @param itemTitle   物品标题或求助标题
     * @param description 补充背景（归还情况、物品状况、用户草稿等），可为空
     * @return AI 生成的评价文本
     */
    public String generateFeedback(String role, String itemTitle, String description) {
        String roleDesc = ROLE_DESC_MAP.getOrDefault(role, role);
        String desc = (description != null && !description.isBlank()) ? description : "无";
        String title = (itemTitle != null && !itemTitle.isBlank()) ? itemTitle : "未知";

        String systemPrompt = String.format(FEEDBACK_SYSTEM_PROMPT, roleDesc, title, desc);

        // 角色专属 User Message，强制 AI 区分借贷方向
        String userMsgTemplate = ROLE_USER_MSG.getOrDefault(role, "请根据以上背景生成一段互助感想。");
        String userContent = String.format(userMsgTemplate, title);

        Map<String, Object> systemMessage = Map.of("role", "system", "content", systemPrompt);
        Map<String, Object> userMessage = Map.of("role", "user", "content", userContent);

        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModelText(),
                "messages", List.of(systemMessage, userMessage),
                "temperature", 0.7,
                "max_tokens", 200
        );

        String content = callApi(requestBody);
        if (content == null || content.isBlank()) {
            throw new AiGenerationException("AI 返回的评价文本为空");
        }

        // 去除可能的引号包裹
        content = content.trim();
        if ((content.startsWith("\"") && content.endsWith("\""))
                || (content.startsWith("'") && content.endsWith("'"))) {
            content = content.substring(1, content.length() - 1).trim();
        }

        return content;
    }

    /**
     * 调用智谱 Chat API 并返回模型生成的文本内容。
     *
     * @param requestBody 请求体（已包含 model、messages 等字段）
     * @return 模型生成的文本
     */
    @SuppressWarnings("unchecked")
    private String callApi(Map<String, Object> requestBody) {
        Map<String, Object> response = chatRestClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + aiConfig.getChatApiKey())
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("choices")) {
            throw new AiGenerationException("Chat API 响应缺少 choices 字段");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new AiGenerationException("Chat API 返回的 choices 数组为空");
        }

        Map<String, Object> choice = choices.get(0);
        Map<String, Object> msg = (Map<String, Object>) choice.get("message");
        if (msg == null) {
            throw new AiGenerationException("Chat API 响应缺少 message 字段");
        }

        return (String) msg.get("content");
    }
}
