package com.platform.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DocumentChunker 知识切片器单元测试 — 覆盖整块保留、按行贪心封卡、超长单行滑窗、
 * 过短丢弃、无标题块方案 B 派生标题。
 */
@DisplayName("DocumentChunker 知识切片器单元测试")
class DocumentChunkerTest {

    private ParsedDocument doc(ParsedBlock... blocks) {
        return new ParsedDocument(List.of(blocks), 0, List.of());
    }

    @Test
    @DisplayName("切片 - 短块整块保留且带章节标题与页码")
    void should_keepWholeBlock_when_shortText() {
        DocumentChunker chunker = new DocumentChunker(10, 15, 3, 2);
        ParsedDocument parsed = doc(new ParsedBlock("第一章", 1, "施工时间规定", 3));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底章节");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).sectionTitle()).isEqualTo("第一章");
        assertThat(chunks.get(0).pageNo()).isEqualTo(3);
        assertThat(chunks.get(0).content()).isEqualTo("施工时间规定");
    }

    @Test
    @DisplayName("切片 - 按行贪心累加，达到目标长度封卡")
    void should_accumulateLines_untilTarget() {
        DocumentChunker chunker = new DocumentChunker(8, 12, 2, 1);
        ParsedDocument parsed = doc(new ParsedBlock("章", 1, "aaaa\nbbbb\ncccc\ndddd", null));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("aaaa\nbbbb");
        assertThat(chunks.get(1).content()).isEqualTo("cccc\ndddd");
    }

    @Test
    @DisplayName("切片 - 行跨过目标但未超硬上限时整行保留")
    void should_keepLineWhole_when_overshootingTargetButUnderMax() {
        DocumentChunker chunker = new DocumentChunker(5, 9, 2, 1);
        ParsedDocument parsed = doc(new ParsedBlock("章", 1, "aaa\nbbb\nccc", null));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("aaa\nbbb");
        assertThat(chunks.get(1).content()).isEqualTo("ccc");
    }

    @Test
    @DisplayName("切片 - 贪心封顶不超硬上限")
    void should_notExceedMax_when_accumulating() {
        DocumentChunker chunker = new DocumentChunker(20, 12, 2, 1);
        ParsedDocument parsed = doc(new ParsedBlock("章", 1, "aaaaa\nbbbbb\nccccc", null));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("aaaaa\nbbbbb");
        assertThat(chunks.get(1).content()).isEqualTo("ccccc");
    }

    @Test
    @DisplayName("切片 - 超长单行按 chunk-max + overlap 滑窗切分")
    void should_slideLongSingleLine_when_exceedsMax() {
        DocumentChunker chunker = new DocumentChunker(10, 10, 2, 1);
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXY"; // 25 字符单行
        ParsedDocument parsed = doc(new ParsedBlock("章", 1, text, null));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底");

        // step = 10 - 2 = 8：0-10, 8-18, 16-25
        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("ABCDEFGHIJ");
        assertThat(chunks.get(1).content()).isEqualTo("IJKLMNOPQR");
        // 相邻切片保留 2 字符重叠
        assertThat(chunks.get(0).content().substring(8)).isEqualTo(chunks.get(1).content().substring(0, 2));
        assertThat(chunks.get(2).content()).isEqualTo("QRSTUVWXY");
    }

    @Test
    @DisplayName("切片 - 滑窗末段短于最短长度时丢弃")
    void should_dropShortSlideTail_when_belowMinChunkLength() {
        DocumentChunker chunker = new DocumentChunker(10, 10, 0, 8);
        String text = "ABCDEFGHIJKLMNOPQRSTUVWXY"; // 25 字符，末段 5 字符 < 8
        ParsedDocument parsed = doc(new ParsedBlock("章", 1, text, null));

        List<Chunk> chunks = chunker.chunk(parsed, "兜底");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("ABCDEFGHIJ");
        assertThat(chunks.get(1).content()).isEqualTo("KLMNOPQRST");
    }

    @Test
    @DisplayName("切片 - 无章节标题的块按方案 B 派生语义标题")
    void should_deriveTitle_when_noSectionTitle() {
        DocumentChunker chunker = new DocumentChunker(400, 550, 60, 2);
        String content = "第一句。".repeat(51); // 204 字符，派生标题截到 200
        ParsedDocument parsed = doc(new ParsedBlock(null, 0, content, null));

        List<Chunk> chunks = chunker.chunk(parsed, "来源名");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).hasSize(204);
        assertThat(chunks.get(0).sectionTitle()).hasSize(200);
        assertThat(chunks.get(0).sectionTitle()).endsWith("句。");
    }

    @Test
    @DisplayName("切片 - 过短块低于最短长度时丢弃")
    void should_dropShortBlock_when_belowMin() {
        DocumentChunker chunker = new DocumentChunker(20, 30, 5, 5);
        ParsedDocument parsed = doc(new ParsedBlock(null, 0, "ab", null));

        assertThat(chunker.chunk(parsed, "兜底")).isEmpty();
    }

    @Test
    @DisplayName("切片 - 空白块与 null 文档跳过不产出切片")
    void should_skipBlankAndNull_when_invalidInput() {
        DocumentChunker chunker = new DocumentChunker(20, 30, 5, 2);
        ParsedDocument parsed = doc(new ParsedBlock(null, 0, "   ", null));

        assertThat(chunker.chunk(null, "兜底")).isEmpty();
        assertThat(chunker.chunk(parsed, "兜底")).isEmpty();
    }
}
