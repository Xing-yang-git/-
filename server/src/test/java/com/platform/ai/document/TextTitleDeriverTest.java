package com.platform.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextTitleDeriver 切片标题派生单元测试 — 按整句切割、首句超限硬截、无句界整段处理。
 */
@DisplayName("TextTitleDeriver 切片标题派生单元测试")
class TextTitleDeriverTest {

    @Test
    @DisplayName("派生 - 贪心取整句，加入下一句超限时停在上一句")
    void should_accumulateWholeSentences_withinLimit() {
        assertThat(TextTitleDeriver.derive("第一句。第二句。第三句。", 9))
                .isEqualTo("第一句。第二句。");
    }

    @Test
    @DisplayName("派生 - 首句即超限时硬截该句前部")
    void should_hardTruncate_when_firstSentenceExceedsLimit() {
        assertThat(TextTitleDeriver.derive("第一句。第二句。", 3)).isEqualTo("第一句");
    }

    @Test
    @DisplayName("派生 - 无句界符时整段作为一句处理")
    void should_treatWholeTextAsSingleSentence_when_noBoundary() {
        assertThat(TextTitleDeriver.derive("无标点短文本", 20)).isEqualTo("无标点短文本");
        assertThat(TextTitleDeriver.derive("无标点超长文本，直接按上限硬截", 6))
                .isEqualTo("无标点超长文本，直接按上限硬截".substring(0, 6));
    }

    @Test
    @DisplayName("派生 - 累计恰好在限内时保留全部")
    void should_keepAll_when_accumulatedExactlyAtLimit() {
        assertThat(TextTitleDeriver.derive("第一句。第二句。", 8)).isEqualTo("第一句。第二句。");
    }

    @Test
    @DisplayName("派生 - 空白文本返回 null")
    void should_returnNull_when_blank() {
        assertThat(TextTitleDeriver.derive(null, 10)).isNull();
        assertThat(TextTitleDeriver.derive("   ", 10)).isNull();
    }
}
