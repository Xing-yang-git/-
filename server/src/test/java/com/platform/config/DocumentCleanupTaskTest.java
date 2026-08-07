package com.platform.config;

import com.platform.common.DocumentStatus;
import com.platform.model.entity.KnowledgeDocument;
import com.platform.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentCleanupTask 定时巡检单元测试 — 覆盖卡死 parsing 重置 failed、过期 failed 文件清理。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentCleanupTask 定时巡检单元测试")
class DocumentCleanupTaskTest {

    @Mock
    private KnowledgeDocumentRepository repository;

    @TempDir
    Path tempDir;

    private DocumentCleanupTask task;

    @BeforeEach
    void setUp() {
        task = new DocumentCleanupTask(repository, tempDir.toString(), 24);
    }

    @Test
    @DisplayName("巡检 - 卡死 parsing 文档重置为 failed 可重试")
    void should_resetStaleParsing_when_stuck() {
        KnowledgeDocument stale1 = KnowledgeDocument.builder().id(1L).fileName("a.txt").status(DocumentStatus.PARSING).build();
        KnowledgeDocument stale2 = KnowledgeDocument.builder().id(2L).fileName("b.pdf").status(DocumentStatus.PARSING).build();
        when(repository.findByStatusAndUpdatedAtBefore(eq(DocumentStatus.PARSING), any())).thenReturn(List.of(stale1, stale2));

        task.resetStaleParsing();

        assertThat(stale1.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(stale1.getErrorMessage()).isEqualTo("解析中断，请重试");
        assertThat(stale2.getStatus()).isEqualTo(DocumentStatus.FAILED);
        verify(repository, times(2)).save(any(KnowledgeDocument.class));
    }

    @Test
    @DisplayName("巡检 - 无卡死 parsing 文档时不保存")
    void should_doNothing_when_noStaleParsing() {
        when(repository.findByStatusAndUpdatedAtBefore(eq(DocumentStatus.PARSING), any())).thenReturn(List.of());

        task.resetStaleParsing();

        verify(repository, never()).save(any(KnowledgeDocument.class));
    }

    @Test
    @DisplayName("清理 - 过期失败文档源文件被删除且记录删除")
    void should_deleteStaleFileAndRecord_when_expired() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("1"));
        Path source = Files.write(dir.resolve("a.txt"), new byte[]{1});
        KnowledgeDocument failed = KnowledgeDocument.builder()
                .id(1L).fileName("a.txt").status(DocumentStatus.FAILED).storagePath("1/a.txt").build();
        when(repository.findByStatusAndUpdatedAtBefore(eq(DocumentStatus.FAILED), any())).thenReturn(List.of(failed));

        task.cleanupStaleFiles();

        assertThat(source).doesNotExist();
        verify(repository).delete(failed);
    }

    @Test
    @DisplayName("清理 - 源文件已不存在时仅删除记录不抛异常")
    void should_deleteRecord_when_sourceFileAlreadyGone() {
        KnowledgeDocument failed = KnowledgeDocument.builder()
                .id(1L).fileName("a.txt").status(DocumentStatus.FAILED).storagePath("1/不存在.txt").build();
        when(repository.findByStatusAndUpdatedAtBefore(eq(DocumentStatus.FAILED), any())).thenReturn(List.of(failed));

        task.cleanupStaleFiles();

        verify(repository).delete(failed);
    }
}
