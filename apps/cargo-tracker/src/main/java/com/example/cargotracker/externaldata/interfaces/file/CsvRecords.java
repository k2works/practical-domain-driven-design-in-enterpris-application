package com.example.cargotracker.externaldata.interfaces.file;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 仮の CSV（RFC 4180）を読んだ結果。ヘッダー（最初の行）、読めた行、読めなかった行の誤りを持つ（Bolt 39）。
 * カンマ区切り、引用符で囲んだ値（中のカンマ・改行、{@code ""} は {@code "} 1 つ）、CRLF・LF、先頭の BOM、最後の行の改行なしを読む。
 * 単独の CR は改行でなく値の中身として読む。空の行は読み飛ばし、行番号はファイルの物理行のまま保つ。
 * 列数の基準は常にヘッダーにし、ヘッダーの行を読めなければほかの行を読まない（ファイルの誤り。{@link CsvHeader#check(CsvRecords, CsvFileKind)}）。
 * 読めない行は例外にせず、物理行の範囲と理由の付いた誤りの値にし、その行の終わり（引用符の対応を追う）から次の行を読む。
 * 引用符の閉じ忘れは後の行を推測で読み直さず、ファイルの終わりまでを 1 つの誤りにする（依存を足さずに自前で読む。開始準備の決定 (4)。
 * 開発レビューの決定。2026-11-02）。
 *
 * @param header ヘッダーの列名（ファイルが空か、ヘッダーの行を読めなければ空）
 * @param rows 読めた行（ヘッダーを除く。ファイルの順）
 * @param errors 読めなかった行の誤り（ファイルの順）
 */
public record CsvRecords(List<String> header, List<CsvRow> rows, List<CsvRowError> errors) {

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final char BOM = (char) 0xFEFF;

    public CsvRecords {
        header = List.copyOf(header);
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
    }

    /** UTF-8 のバイト列を読む。 */
    public static CsvRecords parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return new Parser(text.startsWith(String.valueOf(BOM)) ? text.substring(1) : text).parse();
    }

    /** ヘッダーの行を読めなかったか（ヘッダーがなく、誤りがある）。 */
    public boolean headerUnreadable() {
        return header.isEmpty() && !errors.isEmpty();
    }

    /** 1 レコードを読んだ結果。 */
    private sealed interface Outcome {}

    /** 読めたレコード。 */
    private record Read(CsvRow row, int lastLineNumber) implements Outcome {

        /** 列数の違う行の誤り。 */
        CsvRowError columnCountMismatch() {
            return new CsvRowError(row.lineNumber(), lastLineNumber, CsvRowError.Reason.COLUMN_COUNT_MISMATCH);
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
                return new CsvRecords(List.of(), List.of(), List.of());
            }
            return switch (readRecord()) {
                case Failed failed -> new CsvRecords(List.of(), List.of(), List.of(failed.error()));
                case Read headerRead -> readRows(headerRead.row().values());
            };
        }

        /** ヘッダーの後の行を、ヘッダーの列数を基準に読む。 */
        private CsvRecords readRows(List<String> header) {
            List<CsvRow> rows = new ArrayList<>();
            List<CsvRowError> errors = new ArrayList<>();
            skipBlankLines();
            while (position < text.length()) {
                switch (readRecord()) {
                    case Read read
                    when read.row().values().size() != header.size() -> errors.add(read.columnCountMismatch());
                    case Read read -> rows.add(read.row());
                    case Failed failed -> errors.add(failed.error());
                }
                skipBlankLines();
            }
            return new CsvRecords(header, rows, errors);
        }

        /** 1 レコードを読む。読む位置は次のレコードの先頭に進める。 */
        private Outcome readRecord() {
            int startLine = line;
            List<String> values = new ArrayList<>();
            while (true) {
                StringBuilder value = new StringBuilder();
                CsvRowError.Reason reason = readValue(value);
                if (reason != null) {
                    return fail(startLine, reason);
                }
                values.add(value.toString());
                if (!atValueEnd()) {
                    int lastLine = line;
                    consumeLineEnd();
                    return new Read(new CsvRow(startLine, values), lastLine);
                }
                position++;
            }
        }

        /** 読めなかったレコードの誤りを作り、読む位置をそのレコードの終わりの後に進める。 */
        private Failed fail(int startLine, CsvRowError.Reason reason) {
            int lastLine = reason == CsvRowError.Reason.UNCLOSED_QUOTE ? lastLineOfFile() : skipRestOfRecord();
            return new Failed(new CsvRowError(startLine, lastLine, reason));
        }

        /** 値を 1 つ読む。読む位置は値の後の区切り（カンマ・改行・ファイルの終わり）か、誤りの所に進める。読めなければ理由を返す。 */
        private CsvRowError.Reason readValue(StringBuilder value) {
            if (position >= text.length() || text.charAt(position) != '"') {
                return readUnquoted(value);
            }
            if (!readQuoted(value)) {
                return CsvRowError.Reason.UNCLOSED_QUOTE;
            }
            if (!atValueEnd() && !atRecordEnd()) {
                return CsvRowError.Reason.TEXT_AFTER_CLOSING_QUOTE;
            }
            return null;
        }

        /** 引用符で囲まない値を読む。値の中に引用符があれば、その所で止めて理由を返す。 */
        private CsvRowError.Reason readUnquoted(StringBuilder value) {
            while (!atValueEnd() && !atRecordEnd()) {
                char c = text.charAt(position);
                if (c == '"') {
                    return CsvRowError.Reason.BARE_QUOTE;
                }
                value.append(c);
                position++;
            }
            return null;
        }

        /**
         * 誤りの所からレコードの終わりまでを飛ばし、終わりの物理行の番号を返す。値の始まり（カンマの直後）の引用符は引用符で囲んだ値として
         * 対応を追い、中の改行でレコードを終えない。飛ばす途中で引用符を閉じないままファイルが終われば、ファイルの最後の行を返す。
         */
        private int skipRestOfRecord() {
            boolean valueStart = false;
            while (!atRecordEnd()) {
                char c = text.charAt(position);
                if (valueStart && c == '"') {
                    if (!readQuoted(new StringBuilder())) {
                        return lastLineOfFile();
                    }
                    valueStart = false;
                } else {
                    valueStart = c == ',';
                    position++;
                }
            }
            int lastLine = line;
            consumeLineEnd();
            return lastLine;
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
