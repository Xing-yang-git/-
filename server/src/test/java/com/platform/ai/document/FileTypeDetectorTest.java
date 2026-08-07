package com.platform.ai.document;

import com.platform.common.KnowledgeFileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FileTypeDetector 魔数校验单元测试 — 覆盖各类型命中、伪装后缀拒绝、空文件拒绝。
 */
@DisplayName("FileTypeDetector 魔数校验单元测试")
class FileTypeDetectorTest {

    private byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("校验 - PDF 魔数 %PDF- 命中")
    void should_resolvePdf_when_magicMatches() {
        assertThat(FileTypeDetector.resolveAndValidate("手册.pdf", ascii("%PDF-1.7 ..."))).isEqualTo(KnowledgeFileType.PDF);
    }

    @Test
    @DisplayName("校验 - docx/xlsx ZIP 容器 PK 头命中")
    void should_resolveOoxml_when_zipHeader() {
        assertThat(FileTypeDetector.resolveAndValidate("表.docx", ascii("PK..."))).isEqualTo(KnowledgeFileType.DOCX);
        assertThat(FileTypeDetector.resolveAndValidate("表.xlsx", ascii("PK..."))).isEqualTo(KnowledgeFileType.XLSX);
    }

    @Test
    @DisplayName("校验 - md/txt/csv 纯文本命中")
    void should_resolveText_when_noNulByte() {
        assertThat(FileTypeDetector.resolveAndValidate("说明.md", ascii("# 标题"))).isEqualTo(KnowledgeFileType.MD);
        assertThat(FileTypeDetector.resolveAndValidate("说明.txt", ascii("正文"))).isEqualTo(KnowledgeFileType.TXT);
        assertThat(FileTypeDetector.resolveAndValidate("数据.csv", ascii("a,b"))).isEqualTo(KnowledgeFileType.CSV);
    }

    @Test
    @DisplayName("校验 - 伪装扩展名（pdf 内容非 %PDF-）拒绝")
    void should_reject_when_pdfMagicMismatch() {
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("evil.pdf", ascii("plain text")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不符");
    }

    @Test
    @DisplayName("校验 - 伪装扩展名（docx 内容非 PK）拒绝")
    void should_reject_when_docxMagicMismatch() {
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("evil.docx", ascii("hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不符");
    }

    @Test
    @DisplayName("校验 - 伪装扩展名（txt 含 NUL 字节视为二进制）拒绝")
    void should_reject_when_textContainsNul() {
        byte[] head = new byte[]{(byte) 0x00, (byte) 0x01, (byte) 0x02};
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("evil.txt", head))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容与扩展名不符");
    }

    @Test
    @DisplayName("校验 - 空文件头拒绝")
    void should_reject_when_emptyHead() {
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("a.pdf", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容为空");
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("a.pdf", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件内容为空");
    }

    @Test
    @DisplayName("校验 - 不支持的文件类型拒绝")
    void should_reject_when_unsupportedExtension() {
        assertThatThrownBy(() -> FileTypeDetector.resolveAndValidate("evil.exe", ascii("x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的文件类型");
    }
}
