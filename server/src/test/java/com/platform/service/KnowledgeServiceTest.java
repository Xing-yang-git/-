package com.platform.service;

import com.platform.common.BizStatus;
import com.platform.common.KnowledgeCategory;
import com.platform.model.entity.KnowledgeItem;
import com.platform.repository.KnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeService 知识库业务单元测试 — 覆盖 CRUD、向量生成与分类/状态校验。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeService 知识库业务单元测试")
class KnowledgeServiceTest {

    @Mock
    private KnowledgeItemRepository knowledgeItemRepository;
    @Mock
    private OpenAiEmbeddingModel zhipuEmbedding;

    private KnowledgeService service;

    private float[] vector1024() {
        return new float[1024];
    }

    @BeforeEach
    void setUp() {
        service = new KnowledgeService(knowledgeItemRepository, zhipuEmbedding);
    }

    private KnowledgeItem item(Long id, String category, String status) {
        return KnowledgeItem.builder()
                .id(id).tenantId(1L).category(category)
                .title("装修时间规定").content("工作日 8:00-12:00").status(status).build();
    }

    @Test
    @DisplayName("列表 - 空白关键词规范化为 null")
    void should_list_trimBlankKeyword() {
        Page<KnowledgeItem> page = new PageImpl<>(List.of());
        when(knowledgeItemRepository.findWithFilter(1L, null, null, null, Pageable.unpaged()))
                .thenReturn(page);

        Page<KnowledgeItem> result = service.list(1L, null, null, "   ", Pageable.unpaged());

        assertThat(result).isEqualTo(page);
    }

    @Test
    @DisplayName("查询 - 条目不存在时返回 null")
    void should_get_returnNull_when_absent() {
        when(knowledgeItemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.get(1L)).isNull();
    }

    @Test
    @DisplayName("创建 - 合法分类生成向量并保存")
    void should_create_when_validCategory() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(knowledgeItemRepository.save(any(KnowledgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeItem created = service.create(item(null, KnowledgeCategory.RULES, BizStatus.ONLINE));

        assertThat(created.getEmbedding()).isNotNull().startsWith("[");
        assertThat(created.getEmbedding()).endsWith("]");
        verify(knowledgeItemRepository).save(created);
    }

    @Test
    @DisplayName("创建 - 未知分类抛异常且不落库")
    void should_throw_when_invalidCategory() {
        assertThatThrownBy(() -> service.create(item(null, "bogus", BizStatus.ONLINE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知分类");
        verify(knowledgeItemRepository, never()).save(any(KnowledgeItem.class));
        verify(zhipuEmbedding, never()).embed(anyString());
    }

    @Test
    @DisplayName("创建 - 未知状态抛异常")
    void should_throw_when_invalidStatus() {
        assertThatThrownBy(() -> service.create(item(null, KnowledgeCategory.RULES, "deleted")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知状态");
    }

    @Test
    @DisplayName("创建 - 向量生成失败不阻断保存")
    void should_create_when_embeddingFails() {
        when(zhipuEmbedding.embed(anyString())).thenThrow(new RuntimeException("API down"));
        when(knowledgeItemRepository.save(any(KnowledgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeItem created = service.create(item(null, KnowledgeCategory.RULES, BizStatus.ONLINE));

        assertThat(created.getEmbedding()).isNull();
        verify(knowledgeItemRepository).save(created);
    }

    @Test
    @DisplayName("创建 - 标题正文均为空时跳过向量生成")
    void should_create_skipEmbedding_when_textEmpty() {
        when(knowledgeItemRepository.save(any(KnowledgeItem.class))).thenAnswer(inv -> inv.getArgument(0));
        KnowledgeItem blank = KnowledgeItem.builder()
                .tenantId(1L).category(KnowledgeCategory.RULES).title(null).content(" ").status(BizStatus.ONLINE).build();

        KnowledgeItem created = service.create(blank);

        assertThat(created.getEmbedding()).isNull();
        verify(zhipuEmbedding, never()).embed(anyString());
    }

    @Test
    @DisplayName("更新 - 未携带 id 时抛异常")
    void should_throw_when_updateWithoutId() {
        assertThatThrownBy(() -> service.update(item(null, KnowledgeCategory.RULES, BizStatus.ONLINE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须携带 id");
    }

    @Test
    @DisplayName("更新 - 合法条目重新生成向量并保存")
    void should_update_when_valid() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(knowledgeItemRepository.save(any(KnowledgeItem.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeItem updated = service.update(item(1L, KnowledgeCategory.SERVICE, BizStatus.ONLINE));

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getEmbedding()).isNotNull();
        verify(knowledgeItemRepository).save(updated);
    }

    @Test
    @DisplayName("状态变更 - 条目存在时更新状态")
    void should_setStatus_when_itemExists() {
        KnowledgeItem existing = item(1L, KnowledgeCategory.RULES, BizStatus.ONLINE);
        when(knowledgeItemRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.setStatus(1L, BizStatus.OFFLINE);

        assertThat(existing.getStatus()).isEqualTo(BizStatus.OFFLINE);
        verify(knowledgeItemRepository).save(existing);
    }

    @Test
    @DisplayName("状态变更 - 条目不存在时静默跳过")
    void should_setStatus_noop_when_absent() {
        when(knowledgeItemRepository.findById(1L)).thenReturn(Optional.empty());

        service.setStatus(1L, BizStatus.OFFLINE);

        verify(knowledgeItemRepository, never()).save(any(KnowledgeItem.class));
    }

    @Test
    @DisplayName("批量补齐 - 仅统计生成成功的条目")
    void should_reindex_countEmbedded() {
        KnowledgeItem k1 = item(1L, KnowledgeCategory.RULES, BizStatus.ONLINE);
        KnowledgeItem k2 = item(2L, KnowledgeCategory.SERVICE, BizStatus.ONLINE);
        when(knowledgeItemRepository.findMissing(1L)).thenReturn(List.of(k1, k2));
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());

        int count = service.reindex(1L);

        assertThat(count).isEqualTo(2);
        assertThat(k1.getEmbedding()).isNotNull();
        verify(knowledgeItemRepository, times(2)).save(any(KnowledgeItem.class));
    }

    @Test
    @DisplayName("批量补齐 - 生成失败的条目不计入")
    void should_reindex_skipFailedEmbedding() {
        KnowledgeItem k1 = item(1L, KnowledgeCategory.RULES, BizStatus.ONLINE);
        KnowledgeItem k2 = item(2L, KnowledgeCategory.SERVICE, BizStatus.ONLINE);
        when(knowledgeItemRepository.findMissing(1L)).thenReturn(List.of(k1, k2));
        // 第一条失败，第二条成功
        when(zhipuEmbedding.embed(anyString()))
                .thenThrow(new RuntimeException("API down"))
                .thenReturn(vector1024());

        int count = service.reindex(1L);

        assertThat(count).isEqualTo(1);
        assertThat(k1.getEmbedding()).isNull();
        assertThat(k2.getEmbedding()).isNotNull();
    }
}
