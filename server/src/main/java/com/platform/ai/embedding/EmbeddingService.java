package com.platform.ai.embedding;

import com.platform.model.entity.IdleItem;
import com.platform.repository.IdleItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding 服务，封装向量生成与物品/求助的向量更新逻辑。
 *
 * <p>将标题和描述拼接后调用 Embedding API 生成语义向量，
 * 以 pgvector 的 {@link PGvector} 类型存储到实体中。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final IdleItemRepository idleItemRepository;

    /**
     * 根据标题和描述生成语义向量。
     *
     * @return PGvector 实例，文本为空或 API 失败时返回 null
     */
    public String generateEmbedding(String title, String description) {
        String text = (title != null ? title : "") + " " + (description != null ? description : "");
        text = text.trim();

        if (text.isEmpty()) {
            log.warn("标题和描述均为空，无法生成向量");
            return null;
        }

        float[] vector = embeddingClient.embed(text);
        log.debug("向量生成成功，维度: {}", vector.length);
        return floatArrayToPgvectorString(vector);
    }

    /** 将 float[] 转为 pgvector 字面量格式 '[0.1, 0.2, ...]' */
    static String floatArrayToPgvectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 为闲置物品生成并更新语义向量。
     */
    public void updateItemEmbedding(IdleItem item) {
        try {
            String embedding = generateEmbedding(item.getTitle(), item.getDescription());
            if (embedding != null) {
                item.setEmbedding(embedding);
                idleItemRepository.save(item);
            }
        } catch (Exception e) {
            log.error("为闲置物品 [id={}] 生成向量失败: {}", item.getId(), e.getMessage(), e);
        }
    }

    /**
     * 批量生成所有缺失 embedding 的语义向量。
     */
    public int generateAllMissingEmbeddings() {
        int count = 0;

        List<IdleItem> idleItems = idleItemRepository.findAll();
        for (IdleItem item : idleItems) {
            if (item.getEmbedding() == null || item.getEmbedding().isEmpty()) {
                updateItemEmbedding(item);
                idleItemRepository.save(item);
                count++;
            }
        }

        log.info("批量 Embedding 生成完成: {} 条", count);
        return count;
    }
}
