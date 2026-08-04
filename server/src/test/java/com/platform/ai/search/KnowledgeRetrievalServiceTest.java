package com.platform.ai.search;

import com.platform.model.entity.KnowledgeItem;
import com.platform.repository.KnowledgeItemRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeRetrievalService 知识库 RAG 检索单元测试 — 覆盖向量优先与关键词兜底两级降级。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeRetrievalService 知识库检索单元测试")
class KnowledgeRetrievalServiceTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private OpenAiEmbeddingModel zhipuEmbedding;
    @Mock
    private KnowledgeItemRepository knowledgeItemRepository;
    @Mock
    private Query query;

    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrievalService(entityManager, zhipuEmbedding, knowledgeItemRepository);
        ReflectionTestUtils.setField(service, "topK", 5);
        ReflectionTestUtils.setField(service, "threshold", 0.25);
    }

    private float[] vector1024() {
        return new float[1024];
    }

    @Test
    @DisplayName("检索 - 空白查询直接返回空列表")
    void should_returnEmpty_when_blankQuery() {
        assertThat(service.search(10L, null)).isEmpty();
        assertThat(service.search(10L, "   ")).isEmpty();
        verify(zhipuEmbedding, never()).embed(anyString());
    }

    @Test
    @DisplayName("检索 - 向量命中时返回语义结果")
    void should_returnVectorHits_when_embeddingValid() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of((Object) new Object[]{1L, "装修规定", "工作日施工", "rules", "小区规章", 0.1}));

        List<KnowledgeHit> hits = service.search(10L, "装修几点可以施工");

        assertThat(hits).hasSize(1);
        KnowledgeHit hit = hits.get(0);
        assertThat(hit.id()).isEqualTo(1L);
        assertThat(hit.title()).isEqualTo("装修规定");
        assertThat(hit.source()).isEqualTo("小区规章");
        assertThat(hit.distance()).isEqualTo(0.1);
        // 向量命中时不再走关键词兜底
        verify(knowledgeItemRepository, never()).findWithFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("检索 - 向量结果为空时降级关键词检索")
    void should_fallbackToKeyword_when_vectorEmpty() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        KnowledgeItem item = KnowledgeItem.builder()
                .id(2L).title("垃圾投放").content("分类投放").category("service").source("服务手册").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        List<KnowledgeHit> hits = service.search(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo(2L);
        assertThat(hits.get(0).distance()).isEqualTo(-1.0);   // 非语义排序标记
    }

    @Test
    @DisplayName("检索 - 向量维度异常时降级关键词检索")
    void should_fallbackToKeyword_when_dimensionMismatch() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(new float[10]);
        KnowledgeItem item = KnowledgeItem.builder().id(2L).title("垃圾投放").content("分类投放").category("service").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        List<KnowledgeHit> hits = service.search(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("检索 - 向量生成异常时降级关键词检索")
    void should_fallbackToKeyword_when_embeddingThrows() {
        when(zhipuEmbedding.embed(anyString())).thenThrow(new RuntimeException("API down"));
        KnowledgeItem item = KnowledgeItem.builder().id(2L).title("垃圾投放").content("分类投放").category("service").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        List<KnowledgeHit> hits = service.search(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("检索 - 未绑定小区时跳过向量与关键词检索")
    void should_returnEmpty_when_tenantNull() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());

        List<KnowledgeHit> hits = service.search(null, "垃圾怎么扔");

        assertThat(hits).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString());
        verify(knowledgeItemRepository, never()).findWithFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("检索 - 关键词结果映射为 distance=-1")
    void should_mapKeywordHit_withMinusOneDistance() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        KnowledgeItem item = KnowledgeItem.builder()
                .id(3L).title("搬家公司").content("邻里搬家服务").category("guide").source("办事指南").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));

        List<KnowledgeHit> hits = service.search(10L, "搬家");

        assertThat(hits.get(0).category()).isEqualTo("guide");
        assertThat(hits.get(0).distance()).isEqualTo(-1.0);
    }
}
