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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeRetrievalService 知识库 RAG 检索单元测试 — 覆盖混合召回（向量 ∪ 关键词）与 LLM 重排。
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
    private RerankerService rerankerService;
    @Mock
    private Query query;

    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrievalService(entityManager, zhipuEmbedding, knowledgeItemRepository, rerankerService);
        ReflectionTestUtils.setField(service, "recallTopK", 10);
        ReflectionTestUtils.setField(service, "rerankTopM", 5);
        ReflectionTestUtils.setField(service, "threshold", 0.25);
        ReflectionTestUtils.setField(service, "knowledgeMinScore", 0.3);
    }

    private float[] vector1024() {
        return new float[1024];
    }

    /** 向量 SQL 行的 8 列：id/title/content/category/source/distance/section_title/page_no */
    private Object[] vectorRow(Object... values) {
        return values;
    }

    /** 关键词检索返回空页 */
    private void mockKeywordEmpty() {
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    /** 让 4 参重排直接返回候选（不改序不过滤），供召回路径用例验证命中映射结果 */
    private void stubRerankReturnsCandidates() {
        when(rerankerService.rerank(anyString(), anyList(), anyInt(), anyDouble()))
                .thenAnswer(inv -> (List<KnowledgeHit>) inv.getArgument(1));
    }

    // ==================== searchForAgent（工具专用检索，3b 新增） ====================

    @Test
    @DisplayName("searchForAgent - 向量命中时返回语义结果并映射章节/页码")
    void should_returnVectorHitsForAgent_when_embeddingValid() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of((Object) vectorRow(1L, "装修规定", "工作日施工", "rules", "小区规章", 0.1, "装修施工 / 施工时间", 1)));
        mockKeywordEmpty();
        stubRerankReturnsCandidates();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "装修几点可以施工");

        assertThat(hits).hasSize(1);
        KnowledgeHit hit = hits.get(0);
        assertThat(hit.id()).isEqualTo(1L);
        assertThat(hit.title()).isEqualTo("装修规定");
        assertThat(hit.source()).isEqualTo("小区规章");
        assertThat(hit.distance()).isEqualTo(0.1);
        assertThat(hit.sectionTitle()).isEqualTo("装修施工 / 施工时间");
        assertThat(hit.pageNo()).isEqualTo(1);
        // 混合召回：向量命中后关键词仍会被查询
        verify(knowledgeItemRepository).findWithFilter(any(), any(), any(), anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("searchForAgent - 向量结果为空时用关键词结果")
    void should_fallbackToKeywordForAgent_when_vectorEmpty() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        KnowledgeItem item = KnowledgeItem.builder()
                .id(2L).title("垃圾投放").content("分类投放").category("service").source("服务手册")
                .sectionTitle("垃圾分类管理").pageNo(3).build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        stubRerankReturnsCandidates();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).id()).isEqualTo(2L);
        assertThat(hits.get(0).distance()).isEqualTo(-1.0);   // 非语义排序标记
        assertThat(hits.get(0).sectionTitle()).isEqualTo("垃圾分类管理");
        assertThat(hits.get(0).pageNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("searchForAgent - 向量维度异常时降级关键词检索")
    void should_fallbackToKeywordForAgent_when_dimensionMismatch() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(new float[10]);
        KnowledgeItem item = KnowledgeItem.builder().id(2L).title("垃圾投放").content("分类投放").category("service").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        stubRerankReturnsCandidates();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("searchForAgent - 向量生成异常时降级关键词检索")
    void should_fallbackToKeywordForAgent_when_embeddingThrows() {
        when(zhipuEmbedding.embed(anyString())).thenThrow(new RuntimeException("API down"));
        KnowledgeItem item = KnowledgeItem.builder().id(2L).title("垃圾投放").content("分类投放").category("service").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        stubRerankReturnsCandidates();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "垃圾怎么扔");

        assertThat(hits).hasSize(1);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("searchForAgent - 未绑定小区时跳过向量与关键词检索")
    void should_returnEmptyForAgent_when_tenantNull() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());

        List<KnowledgeHit> hits = service.searchForAgent(null, "垃圾怎么扔");

        assertThat(hits).isEmpty();
        verify(entityManager, never()).createNativeQuery(anyString());
        verify(knowledgeItemRepository, never()).findWithFilter(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("searchForAgent - 向量与关键词命中同一 id 时去重合并")
    void should_dedupByIdForAgent_when_bothHitSameItem() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of((Object) vectorRow(5L, "宠物管理", "遛狗牵绳", "rules", "小区规章", 0.2, null, null)));
        KnowledgeItem item = KnowledgeItem.builder()
                .id(5L).title("宠物管理").content("遛狗牵绳").category("rules").source("小区规章").build();
        when(knowledgeItemRepository.findWithFilter(any(), any(), any(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        stubRerankReturnsCandidates();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "宠物");

        assertThat(hits).hasSize(1);
    }

    @Test
    @DisplayName("searchForAgent - 空白查询直接返回空列表")
    void should_returnEmptyForAgent_when_blankQuery() {
        assertThat(service.searchForAgent(10L, null)).isEmpty();
        assertThat(service.searchForAgent(10L, "   ")).isEmpty();
        verify(zhipuEmbedding, never()).embed(anyString());
    }

    @Test
    @DisplayName("searchForAgent - 召回为空时直接返回空，不进入重排")
    void should_returnEmptyForAgent_when_noRecall() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        mockKeywordEmpty();

        List<KnowledgeHit> hits = service.searchForAgent(10L, "装修");

        assertThat(hits).isEmpty();
        verify(rerankerService, never()).rerank(any(), any(), anyInt(), anyDouble());
    }

    @Test
    @DisplayName("searchForAgent - 召回非空时必走重排并传入分数关卡")
    void should_rerankForAgent_when_mergedNotEmpty() {
        when(zhipuEmbedding.embed(anyString())).thenReturn(vector1024());
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of((Object) vectorRow(1L, "装修规定", "工作日施工", "rules", "小区规章", 0.1, "装修施工", 1)));
        mockKeywordEmpty();
        KnowledgeHit hit = new KnowledgeHit(1L, "装修规定", "工作日施工", "rules", "小区规章", 0.1, "装修施工", 1);
        when(rerankerService.rerank(anyString(), anyList(), anyInt(), anyDouble())).thenReturn(List.of(hit));

        List<KnowledgeHit> hits = service.searchForAgent(10L, "装修");

        // 合并非空必走重排：分数关卡防止关键词召回的不相关内容无把关注入工具结果
        verify(rerankerService).rerank(eq("装修"), anyList(), eq(5), eq(0.3));
        assertThat(hits).hasSize(1);
    }
}
