package com.platform.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextCleaner 文本清洗单元测试 — 覆盖空白归一、页码行去除、相邻重复行、样板文本剔除、过短返回 null。
 */
@DisplayName("TextCleaner 文本清洗单元测试")
class TextCleanerTest {

    @Test
    @DisplayName("清洗 - 行内连续空格与制表符合并为单个空格")
    void should_collapseMultiSpace_when_normalizingBlank() {
        String cleaned = TextCleaner.clean("  施工   时间  \t 规定  ", null);
        assertThat(cleaned).isEqualTo("施工 时间 规定");
    }

    @Test
    @DisplayName("清洗 - CRLF 与 CR 统一为 LF")
    void should_normalizeLineEndings_when_crlfPresent() {
        assertThat(TextCleaner.clean("第一行\r\n第二行\r第三行", null)).isEqualTo("第一行\n第二行\n第三行");
    }

    @Test
    @DisplayName("清洗 - 独立成行的页码被去除（数字 / 第 N 页 / Page N）")
    void should_removePageNumberLines_when_lineIsPageNumber() {
        String cleaned = TextCleaner.clean("第 1 章\n3\n正文\n第 5 页\nPage 2", null);
        assertThat(cleaned).isEqualTo("第 1 章\n正文");
    }

    @Test
    @DisplayName("清洗 - 相邻重复行（重复页眉/页脚）仅保留一条")
    void should_dedupAdjacentDuplicates_when_repeatedLines() {
        String cleaned = TextCleaner.clean("页眉\n页眉\n正文\n页脚\n页脚", null);
        assertThat(cleaned).isEqualTo("页眉\n正文\n页脚");
    }

    @Test
    @DisplayName("清洗 - 独立成行的纯日期行（落款日期）被去除")
    void should_removeDateOnlyLines_when_pureDate() {
        assertThat(TextCleaner.clean("翠湖花园物业管理服务中心\n二〇二六年八月四日\n正文", null))
                .isEqualTo("翠湖花园物业管理服务中心\n正文");
        assertThat(TextCleaner.clean("正文\n2026-08-04\n2026/8/4\n8月4日\n尾", null))
                .isEqualTo("正文\n尾");
    }

    @Test
    @DisplayName("清洗 - 带标签或上下文文字的日期行保留")
    void should_keepLabeledDates_when_notPureDate() {
        assertThat(TextCleaner.clean("生效日期：2026年8月4日\n正文", null))
                .isEqualTo("生效日期：2026年8月4日\n正文");
        assertThat(TextCleaner.clean("本制度自 2026 年 8 月 4 日起施行", null))
                .isEqualTo("本制度自 2026 年 8 月 4 日起施行");
    }

    @Test
    @DisplayName("清洗 - 样板文本整段剔除")
    void should_removeBoilerplate_when_boilerplateProvided() {
        String cleaned = TextCleaner.clean("物业服务手册\n欢迎使用\n物业服务手册\n正文", List.of("物业服务手册"));
        assertThat(cleaned).isEqualTo("欢迎使用\n正文");
    }

    @Test
    @DisplayName("清洗 - 全部空白时返回 null")
    void should_returnNull_when_cleanedEmpty() {
        assertThat(TextCleaner.clean("   \n \n  ", null)).isNull();
    }

    @Test
    @DisplayName("清洗 - null 输入返回 null")
    void should_returnNull_when_rawNull() {
        assertThat(TextCleaner.clean(null, null)).isNull();
    }

    @Test
    @DisplayName("清洗 - 中间空行被折叠为单个换行")
    void should_skipBlankLines_when_inMiddle() {
        String cleaned = TextCleaner.clean("第一段\n\n\n第二段", null);
        assertThat(cleaned).isEqualTo("第一段\n第二段");
    }
}
