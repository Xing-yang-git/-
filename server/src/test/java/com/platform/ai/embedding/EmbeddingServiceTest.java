package com.platform.ai.embedding;

import com.platform.model.entity.IdleItem;
import com.platform.repository.IdleItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbeddingService 向量服务单元测试 — 覆盖向量生成、更新与批量补齐。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmbeddingService 向量服务单元测试")
class EmbeddingServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private IdleItemRepository idleItemRepository;

    private EmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new EmbeddingService(embeddingClient, idleItemRepository);
    }

    @Test
    @DisplayName("生成向量 - 标题描述拼接后返回 pgvector 字面量")
    void should_generateEmbedding_when_validText() {
        when(embeddingClient.embed("电钻 全新博世")).thenReturn(new float[]{0.1f, 0.2f});

        String embedding = service.generateEmbedding("电钻", "全新博世");

        assertThat(embedding).isEqualTo("[0.1,0.2]");
    }

    @Test
    @DisplayName("生成向量 - 标题描述为空时返回 null")
    void should_returnNull_when_textEmpty() {
        assertThat(service.generateEmbedding(null, null)).isNull();
        assertThat(service.generateEmbedding("", "  ")).isNull();
        verify(embeddingClient, never()).embed(anyString());
    }

    @Test
    @DisplayName("更新物品向量 - 生成成功时落库")
    void should_updateItemEmbedding_when_generated() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.5f});
        IdleItem item = IdleItem.builder().id(1L).title("电钻").description("正常").build();

        service.updateItemEmbedding(item);

        assertThat(item.getEmbedding()).isEqualTo("[0.5]");
        verify(idleItemRepository).save(item);
    }

    @Test
    @DisplayName("更新物品向量 - 文本为空时不落库")
    void should_notSave_when_embeddingNull() {
        IdleItem item = IdleItem.builder().id(1L).title("  ").description(null).build();

        service.updateItemEmbedding(item);

        verify(idleItemRepository, never()).save(any(IdleItem.class));
    }

    @Test
    @DisplayName("更新物品向量 - API 异常时静默吞掉不抛出")
    void should_swallowException_when_apiFails() {
        when(embeddingClient.embed(anyString())).thenThrow(new RuntimeException("API down"));
        IdleItem item = IdleItem.builder().id(1L).title("电钻").description("正常").build();

        service.updateItemEmbedding(item);

        assertThat(item.getEmbedding()).isNull();
        verify(idleItemRepository, never()).save(any(IdleItem.class));
    }

    @Test
    @DisplayName("批量补齐 - 仅处理缺失 embedding 的物品")
    void should_generateAllMissingEmbeddings_countMissing() {
        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        IdleItem missing1 = IdleItem.builder().id(1L).title("电钻").description("正常").build();
        IdleItem missing2 = IdleItem.builder().id(2L).title("梯子").description("正常").embedding(null).build();
        IdleItem already = IdleItem.builder().id(3L).title("伞").description("正常").embedding("[0.9]").build();
        when(idleItemRepository.findAll()).thenReturn(List.of(missing1, missing2, already));

        int count = service.generateAllMissingEmbeddings();

        assertThat(count).isEqualTo(2);
        assertThat(missing1.getEmbedding()).isEqualTo("[0.1]");
        assertThat(already.getEmbedding()).isEqualTo("[0.9]");
        // missing1/missing2 各 save 两次（updateItemEmbedding + 批量循环内）
        verify(idleItemRepository, times(2)).save(missing1);
        verify(idleItemRepository, never()).save(already);
    }
}
