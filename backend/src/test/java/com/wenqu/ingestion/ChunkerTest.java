package com.wenqu.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.wenqu.document.api.DocumentBlock;
import com.wenqu.document.api.DocumentBlock.Kind;
import com.wenqu.ingestion.domain.Chunker;
import com.wenqu.ingestion.domain.TextChunk;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChunkerTest {

    /** 测试中每个字符计为一个 token，便于精确控制长度。 */
    private final Chunker chunker = new Chunker(String::length, 100, 20);

    @Test
    void headingsStartNewChunksAndFormThePath() {
        List<TextChunk> chunks = chunker.split(List.of(
                heading(0, 1, "部署"),
                text(1, "准备工作"),
                heading(2, 2, "回滚"),
                text(3, "先切流量"),
                heading(4, 1, "监控"),
                text(5, "看大盘")));

        assertThat(chunks)
                .extracting(TextChunk::headingPath, TextChunk::content, TextChunk::blockFrom, TextChunk::blockTo)
                .containsExactly(tuple("部署", "准备工作", 1, 1), tuple("部署 > 回滚", "先切流量", 3, 3), tuple("监控", "看大盘", 5, 5));
    }

    @Test
    void textBeforeFirstHeadingHasEmptyPath() {
        assertThat(chunker.split(List.of(text(0, "前言"))))
                .singleElement()
                .extracting(TextChunk::headingPath)
                .isEqualTo("");
    }

    @Test
    void blocksAccumulateUpToTheLimitWithOverlap() {
        List<TextChunk> chunks = chunker.split(List.of(
                text(0, "a".repeat(40)), text(1, "b".repeat(40)), text(2, "c".repeat(15)), text(3, "d".repeat(40))));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).blockFrom()).isZero();
        assertThat(chunks.get(0).blockTo()).isEqualTo(2);
        assertThat(chunks.get(0).tokenCount()).isEqualTo(95);
        // 上一块末尾不超过 20 token 的块（c）作为重叠
        assertThat(chunks.get(1).blockFrom()).isEqualTo(2);
        assertThat(chunks.get(1).content()).isEqualTo("c".repeat(15) + "\n" + "d".repeat(40));
    }

    @Test
    void everyChunkRespectsTheLimit() {
        List<DocumentBlock> blocks = IntStream.range(0, 50)
                .mapToObj(i -> text(i, "第" + i + "段内容。".repeat(i % 7 + 1)))
                .toList();

        assertThat(chunker.split(blocks))
                .allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(100));
    }

    @Test
    void oversizedBlockIsSplitBySentences() {
        String sentence = "这是一个十个字的句子。";
        List<TextChunk> chunks = chunker.split(List.of(text(7, sentence.repeat(25))));

        assertThat(chunks).hasSizeGreaterThan(2).allSatisfy(chunk -> {
            assertThat(chunk.tokenCount()).isLessThanOrEqualTo(100);
            assertThat(chunk.blockFrom()).isEqualTo(7);
            assertThat(chunk.blockTo()).isEqualTo(7);
            assertThat(chunk.content()).startsWith("这是");
        });
    }

    @Test
    void englishSentencesKeepTheirSpaces() {
        Chunker small = new Chunker(String::length, 30, 0);

        List<TextChunk> chunks = small.split(List.of(text(0, "One two. Three four. Five six. Seven eight.")));

        assertThat(chunks)
                .extracting(TextChunk::content)
                .containsExactly("One two. Three four. Five six.", "Seven eight.");
    }

    @Test
    void sentenceWithoutBoundaryIsSplitByLength() {
        List<TextChunk> chunks = chunker.split(List.of(code(0, "x".repeat(350))));

        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.tokenCount()).isLessThanOrEqualTo(100));
        assertThat(chunks.stream().mapToInt(TextChunk::tokenCount).sum()).isGreaterThanOrEqualTo(350);
    }

    @Test
    void blankBlocksAndEmptyDocumentsProduceNoChunks() {
        assertThat(chunker.split(List.of())).isEmpty();
        assertThat(chunker.split(List.of(heading(0, 1, "只有标题"), text(1, "  ")))).isEmpty();
    }

    @Test
    void overlapMustBeSmallerThanLimit() {
        assertThatThrownBy(() -> new Chunker(String::length, 100, 100)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DocumentBlock heading(int index, int level, String text) {
        return new DocumentBlock(index, Kind.HEADING, level, text);
    }

    private static DocumentBlock text(int index, String text) {
        return new DocumentBlock(index, Kind.TEXT, 0, text);
    }

    private static DocumentBlock code(int index, String text) {
        return new DocumentBlock(index, Kind.CODE, 0, text);
    }
}
