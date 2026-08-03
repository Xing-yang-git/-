package com.platform.poc;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.List;

/**
 * Step 0.5 技术预研 PoC — 验证 Spring AI 1.0.0 对接三家模型的真实兼容性。
 *
 * <p>验证项：
 * <ol>
 *   <li>deepseek-v4-flash 非流式（含 reasoning_content 剥离）</li>
 *   <li>deepseek-v4-flash tool calling（原生 tools 参数）</li>
 *   <li>智谱 embedding-3 dimensions=1024</li>
 *   <li>智谱 GLM-4V-Flash 多模态（image_url）</li>
 * </ol>
 * PoC 验证已完成（2026-08-03 全部通过），测试类保留作实现参考，
 * 用 @Disabled 防止测试套件误跑消耗 API 额度。
 */
@Disabled("Step 0.5 PoC 已验证完成，防止误跑消耗 API 额度")
class AiCompatibilityPoc {

    private static final String DEEPSEEK_KEY = System.getenv("DEEPSEEK_API_KEY");
    private static final String ZHIPU_KEY = System.getenv("BIGMODEL_EMBEDDING3_KEY");

    /** 工具输入参数 POJO — deepseek 要求 tools 参数 schema 必须是 object 类型 */
    record SearchParams(String keyword) {}

    /** 1. deepseek 非流式 — 验证 content 完整、Spring AI 能解析（reasoning_content 应被忽略） */
    @Test
    void deepseekNonStream() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(DEEPSEEK_KEY)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.2)
                        .maxTokens(500)
                        .build())
                .build();
        ChatResponse resp = model.call(new Prompt("用一句话介绍你自己"));
        System.out.println("=== [PoC] deepseek 非流式 content ===");
        System.out.println(resp.getResult().getOutput().getText());
    }

    /** 2. deepseek tool calling — 验证 Spring AI 的 @Tool/FunctionCallback 走 tools 参数可用 */
    @Test
    void deepseekToolCall() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://api.deepseek.com")
                .apiKey(DEEPSEEK_KEY)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.2)
                        .maxTokens(500)
                        .toolCallbacks(
                                FunctionToolCallback.builder("search_items", (SearchParams p) ->
                                        "{\"title\":\"博世冲击钻\",\"status\":\"online\"}")
                                        .description("搜索闲置物品")
                                        .inputType(SearchParams.class)
                                        .build())
                        .build())
                .build();
        ChatResponse resp = model.call(new Prompt("帮我搜一下电钻"));
        System.out.println("=== [PoC] deepseek tool calling ===");
        System.out.println(resp.getResult().getOutput().getText());
    }

    /** 3. 智谱 embedding-3 dimensions=1024 — 验证返回 1024 维（智谱 base-url 已含 /v4，需自定义 embeddingsPath） */
    @Test
    void zhipuEmbedding() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .embeddingsPath("/embeddings")
                .apiKey(ZHIPU_KEY)
                .build();
        OpenAiEmbeddingModel model = new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model("embedding-3")
                        .dimensions(1024)
                        .build());
        float[] emb = model.embed("测试文本");
        System.out.println("=== [PoC] 智谱 embedding 维度: " + emb.length + " ===");
    }

    /** 4. 智谱 GLM-4V-Flash 多模态 — 验证 image_url 消息经 Spring AI 可用（自定义 completionsPath） */
    @Test
    void zhipuVision() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("https://open.bigmodel.cn/api/paas/v4")
                .completionsPath("/chat/completions")
                .apiKey(ZHIPU_KEY)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("glm-4v-flash")
                        .maxTokens(100)
                        .build())
                .build();
        // 1x1 红色像素 PNG（最小测试图）
        byte[] img = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        UserMessage msg = UserMessage.builder()
                .text("图中是什么颜色")
                .media(List.of(new Media(MimeType.valueOf("image/png"), new ByteArrayResource(img))))
                .build();
        ChatResponse resp = model.call(new Prompt(msg));
        System.out.println("=== [PoC] 智谱多模态 ===");
        System.out.println(resp.getResult().getOutput().getText());
    }
}
