package com.platform.ai.agent;

import com.platform.ai.common.PromptRepository;
import com.platform.model.entity.AgentMemorySegment;
import com.platform.repository.AgentMemorySegmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MemoryRetrievalService 记忆检索单元测试 — 覆盖向量化/无命中/阈值过滤/LLM 整合/降级与越权隔离。
 *
 * <p>降级铁律验证：embedding 失败 / 检索异常（如 pgvector 维度不匹配）/ LLM 失败 / LLM 回复空白
 * → 返回 null 不阻塞对话；检索 SQL 强制按 userId 过滤（越权防护）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemoryRetrievalService 记忆检索单元测试")
class MemoryRetrievalServiceTest {

    @Mock
    private PromptRepository promptRepository;
    @Mock
    private OpenAiChatModel deepseekChatModel;
    @Mock
    private OpenAiEmbeddingModel zhipuEmbedding;
    @Mock
    private AgentMemorySegmentRepository memorySegmentRepository;

    private MemoryRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new MemoryRetrievalService(promptRepository, deepseekChatModel, zhipuEmbedding, memorySegmentRepository);
        ReflectionTestUtils.setField(service, "memoryRecallTop", 3);
        ReflectionTestUtils.setField(service, "memoryMatchThreshold", 0.45);
        // 默认存在压缩段（走完整检索链路）；「无段跳过」用例单独覆盖 0
        lenient().when(memorySegmentRepository.countByUserId(any())).thenReturn(1L);
    }

    /** 1024 维零向量（与压缩段 vector(1024) 一致，满足生产维度校验） */
    private float[] vector() {
        return new float[1024];
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("无段跳过 - 用户无任何压缩段时直接返回「无」，不触发向量化与检索")
    void should_skipEmbedding_when_noSegments() {
        when(memorySegmentRepository.countByUserId(1L)).thenReturn(0L);

        assertThat(service.loadMemoryContext(1L, "你好")).isEqualTo("无");

        verify(zhipuEmbedding, never()).embed(anyString());
        verify(memorySegmentRepository, never()).findIdsBySimilarity(any(), any(), anyInt());
        verify(deepseekChatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("无段跳过 - 数量统计抛异常时降级走原链路（不阻塞、不抛到 chat）")
    void should_fallThrough_when_countThrows() {
        when(memorySegmentRepository.countByUserId(1L)).thenThrow(new RuntimeException("统计失败"));
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3))).thenReturn(List.of());

        assertThat(service.loadMemoryContext(1L, "你好")).isEqualTo("无");

        // 走原链路：统计失败不影响后续向量化与检索
        verify(zhipuEmbedding).embed("你好");
        verify(memorySegmentRepository).findIdsBySimilarity(eq(1L), anyString(), eq(3));
    }

    @Test
    @DisplayName("检索 - 查询文本为空时返回 null，不触发向量化与检索")
    void should_returnNull_when_queryBlank() {
        assertThat(service.loadMemoryContext(1L, null)).isNull();
        assertThat(service.loadMemoryContext(1L, "   ")).isNull();
        verify(memorySegmentRepository, never()).findIdsBySimilarity(any(), any(), anyInt());
    }

    @Test
    @DisplayName("检索 - 向量化失败降级返回 null（不阻塞对话）")
    void should_returnNull_when_embeddingFails() {
        when(zhipuEmbedding.embed("你好")).thenThrow(new RuntimeException("向量失败"));

        assertThat(service.loadMemoryContext(1L, "你好")).isNull();

        verify(memorySegmentRepository, never()).findIdsBySimilarity(any(), any(), anyInt());
        verify(deepseekChatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("检索 - 无命中返回「无」，不调 LLM")
    void should_returnNone_when_noHits() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3))).thenReturn(List.of());

        assertThat(service.loadMemoryContext(1L, "你好")).isEqualTo("无");
        verify(deepseekChatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("检索 - 检索 SQL 强制按 userId 过滤（越权防护）")
    void should_query_byUserId() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3))).thenReturn(List.of());

        service.loadMemoryContext(1L, "你好");

        verify(memorySegmentRepository).findIdsBySimilarity(eq(1L), anyString(), eq(3));
    }

    @Test
    @DisplayName("检索 - 命中按距离阈值过滤（>0.45 不注入），仅距离 ≤ 阈值的摘要进提示词")
    void should_filterByThreshold_when_hitsReturned() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        // 距离 0.9 超过阈值 0.45 → 过滤；距离 0.3 命中（保持距离升序）
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3)))
                .thenReturn(List.<Object[]>of(new Object[]{2L, 0.9}, new Object[]{1L, 0.3}));
        AgentMemorySegment hit = AgentMemorySegment.builder().id(1L).summary("用户喜欢园艺").build();
        when(memorySegmentRepository.findAllById(List.of(1L))).thenReturn(List.of(hit));
        when(promptRepository.get("memory.memory-summary")).thenReturn("记忆整合模板 {segments}");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("整合后的记忆"));

        String ctx = service.loadMemoryContext(1L, "你好");

        assertThat(ctx).isEqualTo("整合后的记忆");
        // 只注入距离 ≤ 阈值的那条摘要（findAllById 只拉取过滤后的 id）
        verify(memorySegmentRepository).findAllById(List.of(1L));
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(deepseekChatModel).call(promptCaptor.capture());
        Message system = promptCaptor.getValue().getInstructions().get(0);
        assertThat(system.getText()).contains("用户喜欢园艺");
    }

    @Test
    @DisplayName("检索 - LLM 整合失败降级返回 null（不阻塞对话）")
    void should_returnNull_when_llmFails() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 0.3}));
        when(memorySegmentRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(AgentMemorySegment.builder().id(1L).summary("摘要").build()));
        when(promptRepository.get("memory.memory-summary")).thenReturn("模板 {segments}");
        when(deepseekChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM 失败"));

        assertThat(service.loadMemoryContext(1L, "你好")).isNull();
    }

    @Test
    @DisplayName("检索 - LLM 回复空白降级返回 null")
    void should_returnNull_when_llmReplyBlank() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 0.3}));
        when(memorySegmentRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(AgentMemorySegment.builder().id(1L).summary("摘要").build()));
        when(promptRepository.get("memory.memory-summary")).thenReturn("模板 {segments}");
        when(deepseekChatModel.call(any(Prompt.class))).thenReturn(response("   "));

        assertThat(service.loadMemoryContext(1L, "你好")).isNull();
    }

    @Test
    @DisplayName("检索 - 向量检索抛异常（如 pgvector 维度不匹配）降级返回 null，不抛到 chat")
    void should_returnNull_when_findIdsBySimilarityThrows() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3)))
                .thenThrow(new RuntimeException("pgvector 维度不匹配"));

        assertThat(service.loadMemoryContext(1L, "你好")).isNull();

        verify(deepseekChatModel, never()).call(any(Prompt.class));
    }

    @Test
    @DisplayName("检索 - 实体回填查询抛异常降级返回 null，不抛到 chat")
    void should_returnNull_when_findAllByIdThrows() {
        when(zhipuEmbedding.embed("你好")).thenReturn(vector());
        when(memorySegmentRepository.findIdsBySimilarity(eq(1L), anyString(), eq(3)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 0.3}));
        when(memorySegmentRepository.findAllById(List.of(1L)))
                .thenThrow(new RuntimeException("pgvector 维度不匹配"));

        assertThat(service.loadMemoryContext(1L, "你好")).isNull();

        verify(deepseekChatModel, never()).call(any(Prompt.class));
    }
}
