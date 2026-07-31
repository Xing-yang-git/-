package com.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * AI 相关配置，提供 Embedding 和 Chat API 调用所需的 RestClient Bean。
 *
 * <p>配置属性来源于 application.yml 中的 ai.embedding.* 和 ai.zhipu.chat.* 节点，
 * 支持通过环境变量 BIGMODEL_EMBEDDING3_KEY 注入 API 密钥。
 * Embedding 和 Chat 均使用智谱 AI（open.bigmodel.cn）的 API。</p>
 */
@Configuration
public class AiConfig {

    /** 智谱 Embedding API 密钥 */
    @Value("${ai.embedding.api-key}")
    private String apiKey;

    /** 智谱 API 基础地址 */
    @Value("${ai.embedding.base-url}")
    private String baseUrl;

    /** Embedding 模型名称（智谱 embedding-3） */
    @Value("${ai.embedding.model}")
    private String model;

    /** 语义相似度阈值（余弦距离），小于此值才视为匹配，默认 0.5 */
    @Value("${ai.embedding.similarity-threshold:0.5}")
    private double similarityThreshold;

    // ==================== 智谱 Chat API 配置（内容审核） ====================

    /** 智谱 Chat API 密钥（复用 Embedding 的密钥环境变量） */
    @Value("${ai.zhipu.chat.api-key}")
    private String chatApiKey;

    /** 智谱 Chat API 基础地址 */
    @Value("${ai.zhipu.chat.base-url}")
    private String chatBaseUrl;

    /** 智谱视觉模型名称（图片审核） */
    @Value("${ai.zhipu.chat.model-vision}")
    private String modelVision;

    /** 智谱文本模型名称（文本审核） */
    @Value("${ai.zhipu.chat.model-text}")
    private String modelText;

    /**
     * 创建 RestClient Bean，用于调用智谱 Embedding API。
     *
     * <p>连接超时和读取超时均为 10 秒，避免 Embedding 调用长时间阻塞。</p>
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
     * 创建 Chat RestClient Bean，用于调用智谱 GLM-4V-Flash / GLM-4-Flash 进行内容审核。
     *
     * <p>连接超时 10 秒，读取超时 30 秒（图片审核可能较慢）。</p>
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
     * 获取 API 密钥。
     *
     * @return 智谱 API 密钥
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 获取 Embedding 模型名称。
     *
     * @return 模型名称，如 embedding-3
     */
    public String getModel() {
        return model;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    /**
     * 获取智谱 Chat API 密钥。
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
     * 获取智谱文本模型名称（文本审核用）。
     *
     * @return 文本模型名称，如 glm-4-flash
     */
    public String getModelText() {
        return modelText;
    }
}
