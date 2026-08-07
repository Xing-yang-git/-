package com.platform.service;

import com.platform.common.DocumentStatus;
import com.platform.model.dto.KnowledgeDocumentDTO;
import com.platform.model.entity.KnowledgeDocument;
import com.platform.repository.KnowledgeDocumentRepository;
import com.platform.repository.KnowledgeItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeDocumentService 文档管理单元测试 — 覆盖上传校验（类型/魔数/大小/同名/MD5）、删除清库、重试。
 *
 * <p>upload 存在多条提前返回分支，文件桩采用 lenient 避免未使用桩报错。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KnowledgeDocumentService 文档管理单元测试")
class KnowledgeDocumentServiceTest {

    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeItemRepository itemRepository;
    @Mock
    private KnowledgeImportService importService;
    @Mock
    private ThreadPoolTaskExecutor documentImportExecutor;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @TempDir
    Path tempDir;

    private KnowledgeDocumentService service;

    @BeforeEach
    void setUp() {
        // 删除内部经 TransactionTemplate 执行短事务：mock getTransaction 返回状态，commit/rollback 空实现
        when(transactionManager.getTransaction(any(DefaultTransactionDefinition.class)))
                .thenReturn(transactionStatus);
        service = new KnowledgeDocumentService(documentRepository, itemRepository, importService,
                documentImportExecutor, tempDir.toString(), transactionManager);
    }

    /** 构造 MultipartFile mock；getInputStream 每次返回新流（头部校验 + MD5 需读两次） */
    private MultipartFile file(String name, byte[] content) {
        MultipartFile f = mock(MultipartFile.class);
        lenient().when(f.getOriginalFilename()).thenReturn(name);
        try {
            lenient().when(f.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(content));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        lenient().when(f.getSize()).thenReturn((long) content.length);
        return f;
    }

    private KnowledgeDocument doc(Long id, Long tenantId, String fileName) {
        return KnowledgeDocument.builder()
                .id(id).tenantId(tenantId).category("rules").fileName(fileName)
                .fileType("txt").source("手册").storagePath(tenantId + "/a.txt")
                .status(DocumentStatus.PARSING).build();
    }

    @Test
    @DisplayName("上传 - 正常文本文件落库并提交异步解析")
    void should_uploadAndSubmit_when_validFile() {
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeDocumentDTO dto = service.upload(file("手册.txt", "正文内容".getBytes()),
                "rules", "标签", null, false, 1L, 99L);

        assertThat(dto.getFileName()).isEqualTo("手册.txt");
        assertThat(dto.getFileType()).isEqualTo("txt");
        assertThat(dto.getStatus()).isEqualTo(DocumentStatus.PARSING);
        assertThat(dto.getSource()).isEqualTo("手册");
        verify(documentRepository).save(any(KnowledgeDocument.class));
        verify(documentImportExecutor).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("上传 - 不支持的扩展名拒绝")
    void should_reject_when_unsupportedType() {
        assertThatThrownBy(() -> service.upload(file("evil.exe", "x".getBytes()), "rules", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("上传 - 非法分类拒绝（KnowledgeCategory 白名单校验）")
    void should_reject_when_invalidCategory() {
        assertThatThrownBy(() -> service.upload(file("手册.txt", "正文".getBytes()), "custom", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知分类");
        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("上传 - 魔数与扩展名不符拒绝")
    void should_reject_when_magicMismatch() {
        assertThatThrownBy(() -> service.upload(file("假.pdf", "不是pdf".getBytes()), "rules", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不符");
    }

    @Test
    @DisplayName("上传 - 超过类型大小上限拒绝")
    void should_reject_when_overSizeLimit() {
        MultipartFile big = file("大文件.txt", new byte[]{'a'});
        when(big.getSize()).thenReturn(6L * 1024 * 1024); // txt 上限 5MB

        assertThatThrownBy(() -> service.upload(big, "rules", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件超过该类型大小上限");
    }

    @Test
    @DisplayName("上传 - 同名文档重复上传拒绝")
    void should_reject_when_sameNameExists() {
        when(documentRepository.findByTenantIdAndFileName(1L, "手册.txt"))
                .thenReturn(Optional.of(doc(1L, 1L, "手册.txt")));

        assertThatThrownBy(() -> service.upload(file("手册.txt", "内容".getBytes()), "rules", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同名文档已存在");
    }

    @Test
    @DisplayName("上传 - replace 同名时先删旧文档再保存新文档")
    void should_replaceOldDoc_when_replaceSameName() {
        KnowledgeDocument old = doc(1L, 1L, "手册.txt");
        when(documentRepository.findByTenantIdAndFileName(1L, "手册.txt")).thenReturn(Optional.of(old));
        when(documentRepository.save(any(KnowledgeDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeDocumentDTO dto = service.upload(file("手册.txt", "新内容".getBytes()),
                "rules", null, null, true, 1L, 1L);

        assertThat(dto.getFileName()).isEqualTo("手册.txt");
        verify(itemRepository).deleteByDocId(1L);
        verify(documentRepository).delete(old);
    }

    @Test
    @DisplayName("上传 - MD5 相同文件重复上传拒绝")
    void should_reject_when_sameMd5() {
        when(documentRepository.findByTenantIdAndFileName(1L, "手册.txt")).thenReturn(Optional.empty());
        when(documentRepository.findByTenantIdAndFileMd5(1L, org.springframework.util.DigestUtils.md5DigestAsHex("重复".getBytes())))
                .thenReturn(Optional.of(doc(1L, 1L, "其他.txt")));

        assertThatThrownBy(() -> service.upload(file("手册.txt", "重复".getBytes()), "rules", null, null, false, 1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("该文件已上传过");
    }

    @Test
    @DisplayName("删除 - 清库全量：切片 + 文档行 + 落盘文件")
    void should_deleteAll_when_validOwnership() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc(1L, 1L, "手册.txt")));

        service.delete(1L, 1L);

        verify(itemRepository).deleteByDocId(1L);
        verify(documentRepository).delete(doc(1L, 1L, "手册.txt"));
    }

    @Test
    @DisplayName("删除 - 其他小区文档拒绝")
    void should_rejectDelete_when_wrongTenant() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc(1L, 2L, "手册.txt")));

        assertThatThrownBy(() -> service.delete(1L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权操作其他小区的文档");
        verify(itemRepository, never()).deleteByDocId(any());
    }

    @Test
    @DisplayName("删除 - 文档不存在抛异常")
    void should_throw_when_docNotFound() {
        when(documentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 1L)).isInstanceOf(RuntimeException.class).hasMessageContaining("文档不存在");
    }

    @Test
    @DisplayName("重试 - 状态重置为 parsing 并提交异步任务")
    void should_resetAndSubmit_when_retry() {
        KnowledgeDocument failed = doc(1L, 1L, "手册.txt");
        failed.setStatus(DocumentStatus.FAILED);
        failed.setErrorMessage("解析失败");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(failed));

        service.retry(1L, 1L);

        assertThat(failed.getStatus()).isEqualTo(DocumentStatus.PARSING);
        assertThat(failed.getErrorMessage()).isNull();
        assertThat(failed.getChunkCount()).isZero();
        verify(documentRepository).save(failed);
        verify(documentImportExecutor).submit(any(Runnable.class));
    }

}
