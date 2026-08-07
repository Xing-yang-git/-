package com.platform.service;

import com.platform.ai.common.AiApiInvoker;
import com.platform.ai.document.Chunk;
import com.platform.ai.document.DocumentChunker;
import com.platform.ai.document.DocumentParser;
import com.platform.ai.document.DocumentParserRegistry;
import com.platform.ai.document.DocumentProcessGuard;
import com.platform.ai.document.ParsedBlock;
import com.platform.ai.document.ParsedDocument;
import com.platform.common.DocumentStatus;
import com.platform.model.entity.KnowledgeDocument;
import com.platform.model.entity.KnowledgeItem;
import com.platform.repository.KnowledgeDocumentRepository;
import com.platform.repository.KnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeImportService 导入编排单元测试 — 覆盖 解析→切片→批量嵌入→入库 主流程、空切片置失败、
 * 文档删除/非解析态丢弃、embedding 失败留空待 reindex。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeImportService 导入编排单元测试")
class KnowledgeImportServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeItemRepository itemRepository;
    @Mock
    private DocumentParserRegistry parserRegistry;
    @Mock
    private DocumentChunker chunker;
    @Mock
    private OpenAiEmbeddingModel zhipuEmbedding;
    @Mock
    private AiApiInvoker aiApiInvoker;
    @Mock
    private DocumentProcessGuard processGuard;
    @Mock
    private DocumentParser parser;
    @Mock
    private PlatformTransactionManager transactionManager;

    private KnowledgeImportService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeImportService(documentRepository, itemRepository, parserRegistry, chunker,
                zhipuEmbedding, aiApiInvoker, processGuard, "./knowledge-docs", 2, 0, transactionManager);
    }

    private KnowledgeDocument parsingDoc(Long id) {
        return KnowledgeDocument.builder()
                .id(id).tenantId(1L).category("rules").fileName("手册.txt")
                .fileType("txt").source("手册").storagePath("1/uuid.txt")
                .status(DocumentStatus.PARSING).build();
    }

    private float[] vec1024(float first) {
        float[] arr = new float[1024];
        arr[0] = first;
        return arr;
    }

    @Test
    @DisplayName("主流程 - 解析成功切片嵌入后入库并标记 ready")
    void should_persistAndMarkReady_when_parseSuccess() throws Exception {
        // Arrange
        KnowledgeDocument doc = parsingDoc(1L);
        when(processGuard.tryAcquire(1L)).thenReturn(true);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(parserRegistry.get("txt")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(new ParsedDocument(
                List.of(new ParsedBlock("第一章", 1, "施工时间规定", null)), 6, List.of()));
        when(chunker.chunk(any(), any())).thenReturn(List.of(new Chunk("第一章", null, "施工时间规定")));
        when(aiApiInvoker.invoke(eq("embedding"), any())).thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
        when(zhipuEmbedding.embed(anyList())).thenReturn(List.of(vec1024(0.5f)));

        // Act
        service.processDocument(1L);

        // Assert
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.getChunkCount()).isEqualTo(1);
        assertThat(doc.getErrorMessage()).isNull();
        verify(itemRepository).deleteByDocId(1L);
        ArgumentCaptor<List<KnowledgeItem>> captor = ArgumentCaptor.<List<KnowledgeItem>>captor();
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getEmbedding()).startsWith("[0.5");
        assertThat(captor.getValue().get(0).getDocId()).isEqualTo(1L);
        assertThat(captor.getValue().get(0).getChunkIndex()).isZero();
        verify(documentRepository).save(doc);
    }

    @Test
    @DisplayName("空切片 - 未提取到可索引文字时标记 failed")
    void should_markFailed_when_noChunks() throws Exception {
        // Arrange
        KnowledgeDocument doc = parsingDoc(2L);
        when(processGuard.tryAcquire(2L)).thenReturn(true);
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));
        when(parserRegistry.get("txt")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(new ParsedDocument(List.of(), 0, List.of()));
        when(chunker.chunk(any(), any())).thenReturn(List.of());

        // Act
        service.processDocument(2L);

        // Assert
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).isEqualTo("未提取到可索引文字");
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("解析异常 - IllegalArgumentException 标记 failed 并带原因")
    void should_markFailed_when_parserThrowsIllegalArgument() throws Exception {
        // Arrange
        KnowledgeDocument doc = parsingDoc(3L);
        when(processGuard.tryAcquire(3L)).thenReturn(true);
        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc));
        when(parserRegistry.get("txt")).thenReturn(parser);
        when(parser.parse(any())).thenThrow(new IllegalArgumentException("文件加密，请解除密码后重新上传"));

        // Act
        service.processDocument(3L);

        // Assert
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(doc.getErrorMessage()).contains("文件加密");
    }

    @Test
    @DisplayName("丢弃 - 文档已删除时切片结果丢弃，不入库")
    void should_discard_when_docDeleted() {
        when(documentRepository.findById(9L)).thenReturn(Optional.empty());

        int persisted = service.persistChunks(9L, List.of(new Chunk("章", null, "内容")), List.of("[0.1]"));

        assertThat(persisted).isZero();
        verify(itemRepository, never()).deleteByDocId(any());
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("丢弃 - 文档非解析态（已被替换）时切片结果丢弃")
    void should_discard_when_docNotParsing() {
        KnowledgeDocument ready = parsingDoc(10L);
        ready.setStatus(DocumentStatus.READY);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(ready));

        int persisted = service.persistChunks(10L, List.of(new Chunk("章", null, "内容")), List.of("[0.1]"));

        assertThat(persisted).isZero();
        verify(itemRepository, never()).deleteByDocId(any());
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("embedding 失败 - 向量留空仍入库，文档告警提示可 reindex")
    void should_leaveEmbeddingNull_when_embeddingFails() throws Exception {
        // Arrange
        KnowledgeDocument doc = parsingDoc(4L);
        when(processGuard.tryAcquire(4L)).thenReturn(true);
        when(documentRepository.findById(4L)).thenReturn(Optional.of(doc));
        when(parserRegistry.get("txt")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(new ParsedDocument(
                List.of(new ParsedBlock("章", 1, "内容", null)), 2, List.of()));
        when(chunker.chunk(any(), any())).thenReturn(List.of(new Chunk("章", null, "内容")));
        when(aiApiInvoker.invoke(eq("embedding"), any())).thenThrow(new RuntimeException("API down"));

        // Act
        service.processDocument(4L);

        // Assert
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(doc.getErrorMessage()).contains("embedding 失败 1 条，可在管理端 reindex 补齐");
        ArgumentCaptor<List<KnowledgeItem>> captor = ArgumentCaptor.<List<KnowledgeItem>>captor();
        verify(itemRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getEmbedding()).isNull();
    }

    @Test
    @DisplayName("防重入 - 文档已在处理中时跳过重复触发")
    void should_skip_when_alreadyProcessing() {
        when(processGuard.tryAcquire(1L)).thenReturn(false);

        service.processDocument(1L);

        verify(documentRepository, never()).findById(any());
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("删除保护 - 处理开始时文档已被删除则直接返回")
    void should_returnEarly_when_docNotFoundAtStart() {
        when(processGuard.tryAcquire(1L)).thenReturn(true);
        when(documentRepository.findById(1L)).thenReturn(Optional.empty());

        service.processDocument(1L);

        verify(itemRepository, never()).saveAll(any());
        verify(documentRepository, never()).save(any(KnowledgeDocument.class));
    }
}
