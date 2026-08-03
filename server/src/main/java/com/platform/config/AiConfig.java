package com.platform.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 相关配置 — 提供三家模型的 Bean 与手写客户端。
 *
 * <p>模型分工（Step 0.5 PoC 已验证兼容性）：
 * <ul>
 *   <li><b>deepseek-v4-flash</b>（OpenAI 兼容）— 文本生成/文本审核/Agent 对话，通用 {@link OpenAiChatModel} 对接</li>
 *   <li><b>智谱 GLM-4V-Flash</b>（OpenAI 兼容）— 图片审核多模态，base-url 已含 /api/paas/v4，需自定义 completionsPath</li>
 *   <li><b>智谱 embedding-3</b>（OpenAI 兼容）— 语义向量，RAG 用 dimensions=1024（与 idle_items 的 2048 区分）</li>
 * </ul>
 *
 * <p><b>铁律：禁止引入 spring-ai-starter-model-deepseek 原生适配器</b>（Function Call 会失效），
 * 统一走 spring-ai-starter-model-openai + base-url 区分厂商。</p>
 *
 * <p>注意：spring.ai.openai.* 自动配置未配置 api-key 时不生效，本类手动构建是唯一模型 Bean。</p>
 */
@Configuration
public class AiConfig {

    // ==================== 智谱 Embedding（语义向量） ====================

    /** 智谱 Embedding API 密钥 */
    @Value("${ai.embedding.api-key}")
    private String apiKey;

    /** 智谱 API 基础地址（已含 /api/paas/v4） */
    @Value("${ai.embedding.base-url}")
    private String baseUrl;

    /** Embedding 模型名称（智谱 embedding-3） */
    @Value("${ai.embedding.model}")
    private String model;

    /** 语义相似度阈值（余弦距离），小于此值才视为匹配 */
    @Value("${ai.embedding.similarity-threshold:0.5}")
    private double similarityThreshold;

    // ==================== 智谱 Chat（图片审核） ====================

    /** 智谱 Chat API 密钥（复用 Embedding 的密钥环境变量） */
    @Value("${ai.zhipu.chat.api-key}")
    private String chatApiKey;

    /** 智谱 Chat API 基础地址 */
    @Value("${ai.zhipu.chat.base-url}")
    private String chatBaseUrl;

    /** 智谱视觉模型名称（图片审核） */
    @Value("${ai.zhipu.chat.model-vision}")
    private String modelVision;

    // ==================== DeepSeek 文本 ====================

    /** DeepSeek API 密钥 */
    @Value("${ai.deepseek.api-key}")
    private String deepseekApiKey;

    /** DeepSeek API 基础地址 */
    @Value("${ai.deepseek.base-url}")
    private String deepseekBaseUrl;

    /** DeepSeek 文本模型名称（deepseek-v4-flash，带 reasoning 思维链） */
    @Value("${ai.deepseek.model}")
    private String deepseekModel;

    // ==================== 手写 RestClient（手写 ModerationClient 图片审核用） ====================

    /**
     * 创建 RestClient Bean，用于调用智谱 Embedding API（现有手写链路）。
     *
     * @return 配置了基础地址和超时的 RestClient 实例
     */
    @Bean
    public RestClient embeddingRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(10000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 创建 Chat RestClient Bean，用于手写 {@code ModerationClient} 图片审核（GLM-4V-Flash）。
     *
     * <p>保留手写而非迁移 Spring AI，避免图片审核回归风险。读取超时 30 秒（视觉审核较慢）。</p>
     *
     * @return 配置了基础地址和超时的 RestClient 实例
     */
    @Bean
    public RestClient chatRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(30000);

        return RestClient.builder()
                .baseUrl(chatBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * 创建 DeepSeek Chat RestClient Bean，用于手写 {@code ModerationClient} 文本审核（deepseek-v4-flash）。
     *
     * <p>文本审核保留手写框架（不迁移 Spring AI），仅模型切 deepseek（minimal regression）。</p>
     *
     * @return 指向 deepseek base-url 的 RestClient 实例
     */
    @Bean
    public RestClient deepseekRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10000);
        requestFactory.setReadTimeout(30000);

        return RestClient.builder()
                .baseUrl(deepseekBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // ==================== Spring AI 模型 Bean ====================

    /**
     * DeepSeek 文本模型 — 文本生成/文本审核/Agent 对话。
     *
     * <p>OpenAI 兼容端点，base-url 指向 deepseek，使用默认 /v1 路径。
     * 温度由各调用方在 Prompt options 中按场景覆盖。</p>
     *
     * @return 配置 deepseek-v4-flash 的 {@link OpenAiChatModel}
     */
    @Bean
    public OpenAiChatModel deepseekChatModel() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(deepseekModel)
                        .maxTokens(512)
                        .build())
                .build();
    }

    /**
     * 智谱视觉模型 — 图片审核（GLM-4V-Flash）。
     *
     * <p>智谱 base-url 已含 /api/paas/v4，Spring AI 默认拼 /v1 会 404，需自定义 completionsPath。</p>
     *
     * @return 配置 glm-4v-flash 的 {@link OpenAiChatModel}
     */
    @Bean
    public OpenAiChatModel zhipuVisionModel() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(chatBaseUrl)
                .completionsPath("/chat/completions")
                .apiKey(chatApiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelVision)
                        .maxTokens(200)
                        .build())
                .build();
    }

    /**
     * 智谱 Embedding 模型 — RAG 知识库向量（1024 维）。
     *
     * <p>智谱 base-url 已含 /api/paas/v4，需自定义 embeddingsPath。
     * dimensions=1024 与 idle_items 的 2048 维区分（Step 0.5 PoC 已验证）。</p>
     *
     * @return 配置 embedding-3 dimensions=1024 的 {@link OpenAiEmbeddingModel}
     */
    @Bean
    public OpenAiEmbeddingModel zhipuEmbedding() {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .embeddingsPath("/embeddings")
                .apiKey(apiKey)
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .model(model)
                        .dimensions(1024)
                        .build());
    }

    // ==================== Getter ====================

    /**
     * 获取智谱 Embedding API 密钥。
     *
     * @return 智谱 API 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取智谱 Embedding 模型名称。
     *
     * @return 模型名称，如 embedding-3
     */
    public String getModel() {
        return model;
    }

    /**
     * 获取语义相似度阈值。
     *
     * @return 余弦距离阈值
     */
    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * 获取智谱 Chat API 密钥（图片审核）。
     *
     * @return Chat API 密钥
     */
    public String getChatApiKey() {
        return chatApiKey;
    }

    /**
     * 获取智谱视觉模型名称（图片审核用）。
     *
     * @return 视觉模型名称，如 glm-4v-flash
     */
    public String getModelVision() {
        return modelVision;
    }

    /**
     * 获取 DeepSeek API 密钥。
     *
     * @return DeepSeek 密钥
     */
    public String getDeepseekApiKey() {
        return deepseekApiKey;
    }

    /**
     * 获取 DeepSeek API 基础地址。
     *
     * @return DeepSeek base-url
     */
    public String getDeepseekBaseUrl() {
        return deepseekBaseUrl;
    }

    /**
     * 获取 DeepSeek 文本模型名称。
     *
     * @return 模型名称，如 deepseek-v4-flash
     */
    public String getDeepseekModel() {
        return deepseekModel;
    }
}
