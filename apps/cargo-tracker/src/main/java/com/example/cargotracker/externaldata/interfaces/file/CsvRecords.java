package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 仮の CSV（RFC 4180）を読んだ結果。ヘッダー（最初の行）、読めた行、読めなかった行の誤り、文字コードの誤りか、物理データ行の数を持つ（Bolt 39。
 * 厳密な UTF-8、行の原文と物理データ行の数は Bolt 39b2）。
 * カンマ区切り、引用符で囲んだ値（中のカンマ・改行、{@code ""} は {@code "} 1 つ）、CRLF・LF、先頭の BOM、最後の行の改行なしを読む。
 * 単独の CR は改行でなく値の中身として読む。空の行は読み飛ばし、行番号はファイルの物理行のまま保つ。
 * UTF-8 だけを受け、不正なバイト列は置き換えずにファイルの誤り（文字コード）にし、行を読まない（Shift_JIS などを推測で変換しない）。
 * 列数の基準は常にヘッダーにし、ヘッダーの行を読めなければほかの行を読まない（ファイルの誤り。{@link CsvHeader#check(CsvRecords, CsvFileKind)}）。
 * 読めない行は例外にせず、物理行の範囲と原文と理由の付いた誤りの値にし、その行の終わり（引用符の対応を追う）から次の行を読む。
 * 引用符の閉じ忘れと、読み飛ばす途中で引用符が閉じないままファイルの終わりまで飲み込んだ行は、後の行を推測で読み直さず、ファイルの終わりまでを
 * 1 つの誤りにする（取込全体の要確認。依存を足さずに自前で読む。開始準備の決定 (4)。開発レビューの決定。2026-11-02）。
 *
 * <p>物理データ行の数（受け取った件数）は、ヘッダーの後の物理行のうちレコードの外の空行を除いた数で、読めた行と誤りの物理行の範囲の和に一致する。
 * 文字コードの誤りと、ヘッダーがファイルの終わりまで飲み込んだときはレコードの境目が分からないので、最初の空でない行をヘッダーとし、その後を空行は
 * 除いて LF で数える（Bolt 39b2 の決定）。
 *
 * @param header ヘッダーの列名（ファイルが空か、ヘッダーの行を読めないか、文字コードの誤りなら空）
 * @param rows 読めた行（ヘッダーを除く。ファイルの順）
 * @param errors 読めなかった行の誤り（ファイルの順）
 * @param undecodable 文字コードの誤り（UTF-8 として読めないバイト列がある）か
 * @param dataLineCount 物理データ行の数（ヘッダーの後の物理行のうち、レコードの外の空行を除いた数）
 * @param firstDataLineNumber 最初の物理データ行の番号（物理データ行がなければ {@code null}。ファイルの誤りの取り込めなかった行の始まり。
 *     Bolt 39b2）
 */
public record CsvRecords(
        List<String> header,
        List<CsvRow> rows,
        List<CsvRowError> errors,
        boolean undecodable,
        int dataLineCount,
        Integer firstDataLineNumber) {

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final char BOM = (char) 0xFEFF;

    public CsvRecords {
        header = List.copyOf(header);
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
        if ((dataLineCount == 0) != (firstDataLineNumber == null)) {
            throw new IllegalArgumentException("物理データ行の数と最初の番号が合わない: " + dataLineCount + "・" + firstDataLineNumber);
        }
    }

    /** UTF-8 のバイト列を厳密に読む。 */
    public static CsvRecords parse(byte[] bytes) {
        String text;
        try {
            text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            // バイトを 1 つずつ文字にして LF を数える（ISO-8859-1 はどのバイト列も 1 バイト 1 文字で読める。LF は UTF-8 でも 1 バイト）
            String bytesAsText = new String(bytes, StandardCharsets.ISO_8859_1);
            return withoutRows(List.of(), true, afterFirst(nonBlankLineNumbers(bytesAsText, 0, 1)));
        }
        return new Parser(text.startsWith(String.valueOf(BOM)) ? text.substring(1) : text).parse();
    }

    /** ヘッダーの行を読めなかったか（ヘッダーがなく、誤りがある）。 */
    public boolean headerUnreadable() {
        return header.isEmpty() && !errors.isEmpty();
    }

    /** 行を読まなかった結果（ヘッダーがなく、物理データ行は空でない物理行の番号の並びから数える）。 */
    private static CsvRecords withoutRows(List<CsvRowError> errors, boolean undecodable, List<Integer> dataLines) {
        return new CsvRecords(
                List.of(),
                List.of(),
                errors,
                undecodable,
                dataLines.size(),
                dataLines.isEmpty() ? null : dataLines.getFirst());
    }

    /** 最初の空でない行（ヘッダー）を除いた並び。 */
    private static List<Integer> afterFirst(List<Integer> lineNumbers) {
        return lineNumbers.isEmpty() ? lineNumbers : lineNumbers.subList(1, lineNumbers.size());
    }

    /**
     * 読む位置から後の、空でない物理行の番号（LF で区切る。空の行と CR だけの行は数えない）。
     *
     * @param lineAtFrom 読む位置の物理行の番号
     */
    private static List<Integer> nonBlankLineNumbers(String text, int from, int lineAtFrom) {
        List<Integer> lineNumbers = new ArrayList<>();
        int start = from;
        int lineNumber = lineAtFrom;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            int lineEnd = end < 0 ? text.length() : end;
            String line = text.substring(start, lineEnd);
            if (!line.isEmpty() && !"\r".equals(line)) {
                lineNumbers.add(lineNumber);
            }
            start = lineEnd + 1;
            lineNumber++;
        }
        return lineNumbers;
    }

    /** 1 レコードを読んだ結果。 */
    private sealed interface Outcome {}

    /** 読めたレコード。 */
    private record Read(CsvRow row) implements Outcome {

        /** 読めたレコードの値の並び。 */
        List<String> values() {
            return row.values();
        }

        /** 列数の違う行の誤り。 */
        CsvRowError columnCountMismatch() {
            return new CsvRowError(
                    row.lineNumber(), row.lastLineNumber(), FormatDefect.COLUMN_COUNT_MISMATCH, row.raw(), false);
        }
    }

    /** 読めなかったレコード。 */
    private record Failed(CsvRowError error) implements Outcome {}

    /** 1 回の解析の状態（読む位置と物理行の番号）。 */
    private static final class Parser {

        private final String text;
        private int position;
        private int line = 1;

        Parser(String text) {
            this.text = text;
        }

        CsvRecords parse() {
            skipBlankLines();
            if (position >= text.length()) {
                return withoutRows(List.of(), false, List.of());
            }
            return switch (readRecord()) {
                case Failed(CsvRowError error) ->
                    withoutRows(
                            List.of(error),
                            false,
                            error.untilEndOfFile()
                                    ? afterFirst(nonBlankLineNumbers(text, 0, 1))
                                    : nonBlankLineNumbers(text, position, line));
                case Read header -> readRows(header.values());
            };
        }

        /** ヘッダーの後の行を、ヘッダーの列数を基準に読む。物理データ行の数は行と誤りの物理行の範囲の和。 */
        private CsvRecords readRows(List<String> header) {
            List<CsvRow> rows = new ArrayList<>();
            List<CsvRowError> errors = new ArrayList<>();
            int dataLineCount = 0;
            Integer firstDataLine = null;
            skipBlankLines();
            while (position < text.length()) {
                if (firstDataLine == null) {
                    firstDataLine = line;
                }
                switch (readRecord()) {
                    case Read read when read.values().size() != header.size() -> errors.add(read.columnCountMismatch());
                    case Read read -> rows.add(read.row());
                    case Failed(CsvRowError error) -> errors.add(error);
                }
                skipBlankLines();
            }
            for (CsvRow row : rows) {
                dataLineCount += row.lastLineNumber() - row.lineNumber() + 1;
            }
            for (CsvRowError error : errors) {
                dataLineCount += error.lastLineNumber() - error.lineNumber() + 1;
            }
            return new CsvRecords(header, rows, errors, false, dataLineCount, firstDataLine);
        }

        /** 1 レコードを読む。読む位置は次のレコードの先頭に進める。 */
        private Outcome readRecord() {
            int startLine = line;
            int start = position;
            List<String> values = new ArrayList<>();
            while (true) {
                StringBuilder value = new StringBuilder();
                FormatDefect defect = readValue(value);
                if (defect != null) {
                    return fail(startLine, start, defect);
                }
                values.add(value.toString());
                if (!atValueEnd()) {
                    int lastLine = line;
                    String raw = text.substring(start, position);
                    consumeLineEnd();
                    return new Read(new CsvRow(startLine, lastLine, values, raw));
                }
                position++;
            }
        }

        /**
         * 読めなかったレコードの誤りを作り、読む位置をそのレコードの終わりの後に進める。引用符の閉じ忘れと、読み飛ばす途中でファイルの終わりまで
         * 飲み込んだ誤りは、ファイルの終わりまでの誤りにする（理由は元のまま）。
         */
        private Failed fail(int startLine, int start, FormatDefect defect) {
            if (defect == FormatDefect.UNCLOSED_QUOTE || !skipRestOfRecord()) {
                return new Failed(new CsvRowError(startLine, lastLineOfFile(), defect, rawToEndOfFile(start), true));
            }
            int lastLine = line;
            String raw = text.substring(start, position);
            consumeLineEnd();
            return new Failed(new CsvRowError(startLine, lastLine, defect, raw, false));
        }

        /** 値を 1 つ読む。読む位置は値の後の区切り（カンマ・改行・ファイルの終わり）か、誤りの所に進める。読めなければ理由を返す。 */
        private FormatDefect readValue(StringBuilder value) {
            if (position >= text.length() || text.charAt(position) != '"') {
                return readUnquoted(value);
            }
            if (!readQuoted(value)) {
                return FormatDefect.UNCLOSED_QUOTE;
            }
            if (!atValueEnd() && !atRecordEnd()) {
                return FormatDefect.TEXT_AFTER_CLOSING_QUOTE;
            }
            return null;
        }

        /** 引用符で囲まない値を読む。値の中に引用符があれば、その所で止めて理由を返す。 */
        private FormatDefect readUnquoted(StringBuilder value) {
            while (!atValueEnd() && !atRecordEnd()) {
                char c = text.charAt(position);
                if (c == '"') {
                    return FormatDefect.BARE_QUOTE;
                }
                value.append(c);
                position++;
            }
            return null;
        }

        /**
         * 誤りの所からレコードの終わり（行末の改行の前）までを飛ばす。値の始まり（カンマの直後）の引用符は引用符で囲んだ値として対応を追い、
         * 中の改行でレコードを終えない。飛ばす途中で引用符を閉じないままファイルが終われば false（ファイルの終わりまで飲み込んだ）。
         */
        private boolean skipRestOfRecord() {
            boolean valueStart = false;
            while (!atRecordEnd()) {
                char c = text.charAt(position);
                if (valueStart && c == '"') {
                    if (!readQuoted(new StringBuilder())) {
                        return false;
                    }
                    valueStart = false;
                } else {
                    valueStart = c == ',';
                    position++;
                }
            }
            return true;
        }

        /** 引用符で囲んだ値を読む。閉じる引用符がないままファイルが終われば false。 */
        private boolean readQuoted(StringBuilder value) {
            position++;
            while (position < text.length()) {
                char c = text.charAt(position++);
                if (c != '"') {
                    if (c == '\n') {
                        line++;
                    }
                    value.append(c);
                } else if (position < text.length() && text.charAt(position) == '"') {
                    value.append('"');
                    position++;
                } else {
                    return true;
                }
            }
            return false;
        }

        /** ファイルの最後の物理行の番号（読む位置がファイルの終わりにあるとき。最後の改行は行を増やさない）。 */
        private int lastLineOfFile() {
            return text.endsWith("\n") ? line - 1 : line;
        }

        /** 始まりからファイルの終わりまでの原文（最後の行末の改行を除く）。 */
        private String rawToEndOfFile(int start) {
            int end = text.length();
            if (text.endsWith("\r\n")) {
                end -= 2;
            } else if (text.endsWith("\n")) {
                end -= 1;
            }
            return text.substring(start, Math.max(start, end));
        }

        private void skipBlankLines() {
            while (position < text.length() && atLineEnd()) {
                consumeLineEnd();
            }
        }

        /** 読む位置が値の区切りのカンマか。 */
        private boolean atValueEnd() {
            return position < text.length() && text.charAt(position) == ',';
        }

        /** 読む位置がレコードの終わり（改行かファイルの終わり）か。 */
        private boolean atRecordEnd() {
            return position >= text.length() || atLineEnd();
        }

        private boolean atLineEnd() {
            char c = text.charAt(position);
            return c == '\n' || (c == '\r' && position + 1 < text.length() && text.charAt(position + 1) == '\n');
        }

        private void consumeLineEnd() {
            if (position < text.length()) {
                position += text.charAt(position) == '\r' ? 2 : 1;
                line++;
            }
        }
    }
}
