package com.example.cargotracker.externaldata.interfaces.file;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 仮の CSV（RFC 4180）を読んだ結果。読めた行（ヘッダーを含む）と、読めなかった行の誤りを持つ（Bolt 39）。
 * カンマ区切り、引用符で囲んだ値（中のカンマ・改行、{@code ""} は {@code "} 1 つ）、CRLF・LF、先頭の BOM、最後の行の改行なしを読む。
 * 読めない行は例外にせず行番号と理由の付いた誤りの値にし、ほかの行は読む（依存を足さずに自前で読む。開始準備の決定 (4)）。
 *
 * @param rows 読めた行（ファイルの順）
 * @param errors 読めなかった行の誤り（ファイルの順）
 */
public record CsvRecords(List<CsvRow> rows, List<CsvRowError> errors) {

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final char BOM = (char) 0xFEFF;

    public CsvRecords {
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
    }

    /** UTF-8 のバイト列を読む。 */
    public static CsvRecords parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return new Parser(text.startsWith(String.valueOf(BOM)) ? text.substring(1) : text).parse();
    }

    /** 1 回の解析の状態（読む位置と行番号）。 */
    private static final class Parser {

        private final String text;
        private int position;
        private int line = 1;

        Parser(String text) {
            this.text = text;
        }

        CsvRecords parse() {
            List<CsvRow> rows = new ArrayList<>();
            List<CsvRowError> errors = new ArrayList<>();
            while (position < text.length()) {
                int startLine = line;
                List<String> values = new ArrayList<>();
                CsvRowError.Reason reason = readRecord(values);
                if (reason == null
                        && !rows.isEmpty()
                        && values.size() != rows.getFirst().values().size()) {
                    reason = CsvRowError.Reason.COLUMN_COUNT_MISMATCH;
                }
                if (reason == null) {
                    rows.add(new CsvRow(startLine, values));
                } else {
                    errors.add(new CsvRowError(startLine, reason));
                }
            }
            return new CsvRecords(rows, errors);
        }

        /** 1 レコードを読み、値を {@code values} に足す。読めなければ理由を返す（読む位置は次のレコードの先頭に進める）。 */
        private CsvRowError.Reason readRecord(List<String> values) {
            while (true) {
                StringBuilder value = new StringBuilder();
                CsvRowError.Reason reason = readValue(value);
                if (reason != null) {
                    return reason;
                }
                values.add(value.toString());
                if (!atValueEnd()) {
                    consumeLineEnd();
                    return null;
                }
                position++;
            }
        }

        /** 値を 1 つ読む。読む位置は値の後の区切り（カンマ・改行・ファイルの終わり）に進める。読めなければ理由を返す。 */
        private CsvRowError.Reason readValue(StringBuilder value) {
            if (position >= text.length() || text.charAt(position) != '"') {
                return readUnquoted(value);
            }
            if (!readQuoted(value)) {
                return CsvRowError.Reason.UNCLOSED_QUOTE;
            }
            if (!atValueEnd() && !atRecordEnd()) {
                skipToNextLine();
                return CsvRowError.Reason.TEXT_AFTER_CLOSING_QUOTE;
            }
            return null;
        }

        /** 引用符で囲まない値を読む。値の中に引用符があれば、その行の残りを飛ばして理由を返す。 */
        private CsvRowError.Reason readUnquoted(StringBuilder value) {
            while (!atValueEnd() && !atRecordEnd()) {
                char c = text.charAt(position);
                if (c == '"') {
                    skipToNextLine();
                    return CsvRowError.Reason.BARE_QUOTE;
                }
                value.append(c);
                position++;
            }
            return null;
        }

        /** 読む位置が値の区切りのカンマか。 */
        private boolean atValueEnd() {
            return position < text.length() && text.charAt(position) == ',';
        }

        /** 読む位置がレコードの終わり（改行かファイルの終わり）か。 */
        private boolean atRecordEnd() {
            return position >= text.length() || atLineEnd();
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

        private void skipToNextLine() {
            while (position < text.length() && !atLineEnd()) {
                position++;
            }
            consumeLineEnd();
        }
    }
}
