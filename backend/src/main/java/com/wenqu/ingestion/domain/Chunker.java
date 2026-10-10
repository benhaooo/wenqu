package com.wenqu.ingestion.domain;

import com.wenqu.document.api.DocumentBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 按标题分节，节内按块累积成不超过 maxTokens 的文本块，相邻块重叠约 overlapTokens。
 *
 * <ul>
 *   <li>标题不跨块：遇到标题时结束当前块，标题本身记入标题路径，不计入正文。
 *   <li>超过 maxTokens 的块按句子拆开；单个句子仍然超长时按字符数切开。
 *   <li>重叠以整块（或拆开后的整句）为单位，保证每个文本块对应的正文块范围准确。
 * </ul>
 */
public final class Chunker {

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；!?;])|(?<=\\.)(?=\\s)|\\n");

    private final ToIntFunction<String> tokenCounter;
    private final int maxTokens;
    private final int overlapTokens;

    public Chunker(ToIntFunction<String> tokenCounter, int maxTokens, int overlapTokens) {
        if (overlapTokens >= maxTokens) {
            throw new IllegalArgumentException("overlapTokens must be less than maxTokens");
        }
        this.tokenCounter = tokenCounter;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<TextChunk> split(List<DocumentBlock> blocks) {
        List<TextChunk> chunks = new ArrayList<>();
        Deque<Heading> headings = new ArrayDeque<>();
        List<Unit> current = new ArrayList<>();
        for (DocumentBlock block : blocks) {
            if (block.kind() == DocumentBlock.Kind.HEADING) {
                emit(current, headings, chunks);
                current.clear();
                while (!headings.isEmpty() && headings.peekLast().level() >= block.headingLevel()) {
                    headings.removeLast();
                }
                if (!block.text().isBlank()) {
                    headings.addLast(
                            new Heading(block.headingLevel(), block.text().strip()));
                }
                continue;
            }
            for (Unit unit : units(block)) {
                if (!current.isEmpty() && tokens(current) + unit.tokens() > maxTokens) {
                    emit(current, headings, chunks);
                    List<Unit> overlap = overlap(current, unit.tokens());
                    current.clear();
                    current.addAll(overlap);
                }
                current.add(unit);
            }
        }
        emit(current, headings, chunks);
        return chunks;
    }

    /** 块拆成不超过 maxTokens 的单元。 */
    private List<Unit> units(DocumentBlock block) {
        String text = block.text().strip();
        if (text.isEmpty()) {
            return List.of();
        }
        int count = tokenCounter.applyAsInt(text);
        if (count <= maxTokens) {
            return List.of(new Unit(block.index(), text, count));
        }
        List<Unit> units = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int pieceTokens = 0;
        for (String sentence : SENTENCE_END.split(text)) {
            String trimmed = sentence.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int sentenceTokens = tokenCounter.applyAsInt(trimmed);
            if (sentenceTokens > maxTokens) {
                if (!piece.isEmpty()) {
                    units.add(new Unit(block.index(), piece.toString(), pieceTokens));
                    piece.setLength(0);
                    pieceTokens = 0;
                }
                units.addAll(splitByLength(block.index(), trimmed, sentenceTokens));
                continue;
            }
            if (pieceTokens + sentenceTokens > maxTokens) {
                units.add(new Unit(block.index(), piece.toString(), pieceTokens));
                piece.setLength(0);
                pieceTokens = 0;
            }
            appendSentence(piece, trimmed);
            pieceTokens += sentenceTokens;
        }
        if (!piece.isEmpty()) {
            units.add(new Unit(block.index(), piece.toString(), pieceTokens));
        }
        return units;
    }

    /** 拆句时去掉了句间空白，西文句子之间补回空格。 */
    private static void appendSentence(StringBuilder piece, String sentence) {
        if (!piece.isEmpty() && piece.charAt(piece.length() - 1) < 0x80 && sentence.charAt(0) < 0x80) {
            piece.append(' ');
        }
        piece.append(sentence);
    }

    /** 没有句子边界的超长文本（如长代码行）按字符数等分。 */
    private List<Unit> splitByLength(int blockIndex, String text, int tokens) {
        int parts = Math.ceilDiv(tokens, maxTokens) + 1;
        int length = Math.ceilDiv(text.length(), parts);
        List<Unit> units = new ArrayList<>();
        for (int start = 0; start < text.length(); start += length) {
            String part = text.substring(start, Math.min(text.length(), start + length));
            units.add(new Unit(blockIndex, part, tokenCounter.applyAsInt(part)));
        }
        return units;
    }

    /** 上一块末尾不超过 overlapTokens 的单元，且加上下一个单元后不超过 maxTokens。 */
    private List<Unit> overlap(List<Unit> previous, int nextTokens) {
        Deque<Unit> overlap = new ArrayDeque<>();
        int total = 0;
        for (int i = previous.size() - 1; i >= 0; i--) {
            Unit unit = previous.get(i);
            if (total + unit.tokens() > overlapTokens || total + unit.tokens() + nextTokens > maxTokens) {
                break;
            }
            overlap.addFirst(unit);
            total += unit.tokens();
        }
        return List.copyOf(overlap);
    }

    private static void emit(List<Unit> units, Deque<Heading> headings, List<TextChunk> chunks) {
        if (units.isEmpty()) {
            return;
        }
        String content = units.stream().map(Unit::text).collect(Collectors.joining("\n"));
        String headingPath = headings.stream().map(Heading::text).collect(Collectors.joining(" > "));
        chunks.add(new TextChunk(
                headingPath,
                content,
                tokens(units),
                units.getFirst().blockIndex(),
                units.getLast().blockIndex()));
    }

    private static int tokens(List<Unit> units) {
        return units.stream().mapToInt(Unit::tokens).sum();
    }

    private record Unit(int blockIndex, String text, int tokens) {}

    private record Heading(int level, String text) {}
}
