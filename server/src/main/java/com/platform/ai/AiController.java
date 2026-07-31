package com.platform.ai;

import com.platform.ai.embedding.EmbeddingService;
import com.platform.common.Result;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 功能管理端点（仅管理员可访问）。
 *
 * <p>提供语义向量批量生成等管理操作。
 * 访问权限由 {@link com.platform.config.SecurityConfig} 中的角色校验控制，
 * 仅 {@code ROLE_ADMIN} 和 {@code ROLE_SUPER_ADMIN} 可调用。</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final EmbeddingService embeddingService;

    public AiController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /**
     * 批量生成所有缺失 embedding 的语义向量。
     * 用于数据迁移后补全向量数据，供语义搜索和供需匹配使用。
     *
     * @param auth 当前认证用户（由 SecurityConfig 保证为管理员角色）
     * @return 本次生成的向量数量
     */
    @PostMapping("/generate-embeddings")
    public Result<?> generateEmbeddings(Authentication auth) {
        int count = embeddingService.generateAllMissingEmbeddings();
        return Result.ok(Map.of("count", count));
    }
}
