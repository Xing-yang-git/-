package com.platform.ai.search;

import com.platform.ai.common.AiApiInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RerankerService 重排服务单元测试 — 覆盖按相关性分数重排、失败降级原序、候选过少直接返回、topM 截断。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RerankerService 重排服务单元测试")
class RerankerServiceTest {

    @Mock
    private RestClient rerankRestClient;
    @Mock
    private RestClient.RequestBodyUriSpec postSpec;
    @Mock
    private RestClient.RequestBodySpec bodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    @Mock
    private AiApiInvoker aiApiInvoker;

    private RerankerService service;

    @BeforeEach
    void setUp() {
        service = new RerankerService(rerankRestClient, aiApiInvoker, "bge-reranker-v2-m3");
        // lenient：部分用例（invoke 抛异常 / 候选过少）不会走到 HTTP 链
        lenient().when(rerankRestClient.post()).thenReturn(postSpec);
        lenient().when(postSpec.uri(anyString())).thenReturn(bodySpec);
        lenient().when(bodySpec.contentType(any())).thenReturn(bodySpec);
        lenient().when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        lenient().when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    private KnowledgeHit hit(long id, String title) {
        return new KnowledgeHit(id, title, "内容-" + id, "rules", "手册", 0.1, null, null);
    }

    /** 模拟本地 rerank-service（兼容 Ollama /api/rerank 契约）返回 index→分数 */
    private void stubRerankResults(List<Map<String, Object>> results) {
        when(responseSpec.body(Map.class)).thenReturn(Map.of("results", results));
    }

    /** 让 aiApiInvoker.invoke 直接执行重排动作（不重试） */
    private void stubInvokeRunsAction() {
        when(aiApiInvoker.invoke(anyString(), anyInt(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get());
    }

    @Test
    @DisplayName("重排4参 - 按相关性分数降序重排（minScore=0 不过滤）")
    void should_rerankByScore_when_validResponse() {
        stubInvokeRunsAction();
        stubRerankResults(List.of(
                Map.of("index", 2, "relevance_score", 0.9),
                Map.of("index", 0, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.3)));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 10, 0.0);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("C", "A", "B");
    }

    @Test
    @DisplayName("重排4参 - 重排结果超过 topM 时截断（minScore=0 不过滤）")
    void should_truncateToTopM_when_resultExceeds() {
        stubInvokeRunsAction();
        stubRerankResults(List.of(
                Map.of("index", 2, "relevance_score", 0.9),
                Map.of("index", 0, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.3)));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 2, 0.0);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("C", "A");
    }

    @Test
    @DisplayName("重排4参 - 响应缺少 results 时降级原序")
    void should_degradeToOriginal_when_missingResults() {
        stubInvokeRunsAction();
        when(responseSpec.body(Map.class)).thenReturn(Map.of());

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 10, 0.5);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("重排4参 - results 为空时降级原序")
    void should_degradeToOriginal_when_emptyResults() {
        stubInvokeRunsAction();
        stubRerankResults(List.of());

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B")), 10, 0.5);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("A", "B");
    }

    @Test
    @DisplayName("重排4参 - 外部调用异常时降级原序")
    void should_degradeToOriginal_when_invokeThrows() {
        when(aiApiInvoker.invoke(anyString(), anyInt(), any())).thenThrow(new RuntimeException("rerank-service down"));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B")), 10, 0.9);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("A", "B");
    }

    // ==================== 重排 4 参（分数关卡，3b 新增） ====================

    @Test
    @DisplayName("重排4参 - 按相关性分数过滤低于 minScore 的条目")
    void should_filterBelowMinScore_when_fourArg() {
        stubInvokeRunsAction();
        stubRerankResults(List.of(
                Map.of("index", 2, "relevance_score", 0.9),
                Map.of("index", 0, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.2)));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 10, 0.4);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("C", "A");
    }

    @Test
    @DisplayName("重排4参 - 全部低于 minScore 时返回空列表（不抛异常，由调用方按未命中处理）")
    void should_returnEmpty_when_allBelowMinScore() {
        stubInvokeRunsAction();
        stubRerankResults(List.of(
                Map.of("index", 0, "relevance_score", 0.2),
                Map.of("index", 1, "relevance_score", 0.1)));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B")), 10, 0.4);

        // 全部低于阈值 → 空结果，而非抛异常降级原序（避免把不相关候选喂给模型）
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("重排4参 - 分数过滤后按 topM 截断")
    void should_truncateAfterFilter_when_fourArg() {
        stubInvokeRunsAction();
        stubRerankResults(List.of(
                Map.of("index", 2, "relevance_score", 0.9),
                Map.of("index", 0, "relevance_score", 0.5),
                Map.of("index", 1, "relevance_score", 0.2)));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 1, 0.4);

        assertThat(result).extracting(KnowledgeHit::title).containsExactly("C");
    }

    @Test
    @DisplayName("重排4参 - 重排失败降级原序并截断 topM（无分数不过滤）")
    void should_degradeOriginalTruncated_when_fourArgInvokeThrows() {
        when(aiApiInvoker.invoke(anyString(), anyInt(), any())).thenThrow(new RuntimeException("rerank-service down"));

        List<KnowledgeHit> result = service.rerank("问题", List.of(hit(1, "A"), hit(2, "B"), hit(3, "C")), 2, 0.9);

        // 降级路径无分数可用，不过滤（即使 minScore 较高），仅截断 topM
        assertThat(result).extracting(KnowledgeHit::title).containsExactly("A", "B");
    }

    @Test
    @DisplayName("重排4参 - 候选为 1 条或 null 时直接返回，不调用服务")
    void should_returnAsIs_when_tooFewCandidatesFourArg() {
        List<KnowledgeHit> single = List.of(hit(1, "A"));

        assertThat(service.rerank("问题", single, 10, 0.5)).isSameAs(single);
        assertThat(service.rerank("问题", null, 10, 0.5)).isNull();
        verify(aiApiInvoker, never()).invoke(anyString(), anyInt(), any());
    }
}
