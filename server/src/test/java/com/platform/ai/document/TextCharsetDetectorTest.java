package com.platform.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextCharsetDetector 字符集检测单元测试 — 覆盖 BOM 优先 / UTF-8 严格校验 / GBK 启发式兜底。
 */
@DisplayName("TextCharsetDetector 字符集检测单元测试")
class TextCharsetDetectorTest {

    @Test
    @DisplayName("检测 - UTF-8 BOM (EF BB BF) 返回 UTF-8")
    void should_detectUtf8_when_bomPresent() {
        byte[] bytes = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i'};
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("检测 - UTF-16LE BOM (FF FE) 返回 UTF-16LE")
    void should_detectUtf16le_when_leBomPresent() {
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xFE, 'a', 0, 'b', 0};
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_16LE);
    }

    @Test
    @DisplayName("检测 - UTF-16BE BOM (FE FF) 返回 UTF-16BE")
    void should_detectUtf16be_when_beBomPresent() {
        byte[] bytes = new byte[]{(byte) 0xFE, (byte) 0xFF, 0, 'a', 0, 'b'};
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_16BE);
    }

    @Test
    @DisplayName("检测 - 纯 UTF-8 字节（含中文）返回 UTF-8")
    void should_detectUtf8_when_validUtf8Bytes() {
        byte[] bytes = "你好，社区".getBytes(StandardCharsets.UTF_8);
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("检测 - ASCII 纯英文返回 UTF-8")
    void should_detectUtf8_when_plainAscii() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.US_ASCII);
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("检测 - GBK 高位字节序列（非法 UTF-8）返回 GBK")
    void should_detectGbk_when_highByteSequence() {
        byte[] bytes = "中文文档".getBytes(Charset.forName("GBK"));
        assertThat(TextCharsetDetector.detect(bytes)).isEqualTo(Charset.forName("GBK"));
    }

    @Test
    @DisplayName("检测 - 空字节数组返回 UTF-8 兜底")
    void should_detectUtf8_when_emptyBytes() {
        assertThat(TextCharsetDetector.detect(new byte[0])).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("检测 - null 返回 UTF-8 兜底")
    void should_detectUtf8_when_nullBytes() {
        assertThat(TextCharsetDetector.detect(null)).isEqualTo(StandardCharsets.UTF_8);
    }
}
