package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 取込の組み立て。CSV の解析（{@link CsvRecords}）とヘッダーの検証（{@link CsvHeader}）の結果から、値だけの取込のコマンド
 * （{@link IngestFileCommand}）を作る関数（UC-15 2a。Bolt 39b2）。Spring の部品にせず（画面の層がつなぐ。39c）、リポジトリを使わない。
 *
 * <p>ファイルの誤り（文字コード・ヘッダーが読めない・必要な列がない・同じ名前の列）は行を読まず、取り込めなかった部分（データ行があれば
 * 最初のデータ行から受け取った件数のすべて、なければ始まりなしで 0）にする。引用符が閉じないままファイルの終わりまで飲み込んだ範囲は原文付きの
 * 取り込めなかった部分に、ほかの読めなかった行は行の誤りにする。読めた行はキーの列（外部 ID・外部版・取得時刻）だけを検証し、空（空白だけを含む）・
 * 長すぎる（表の列の上限）・時刻の書式の誤りを行の誤りにする。値は加工せずに渡し、原文は UTF-8 のバイト列にする（値の列は 39b2b）。
 */
public final class IngestFileCommandAssembler {

    private static final String EXTERNAL_ID = "external_id";
    private static final String EXTERNAL_VERSION = "external_version";
    private static final String ACQUIRED_AT = "acquired_at";

    /** 外部 ID の上限の文字数（{@code external_record.external_id VARCHAR(200)}）。 */
    private static final int EXTERNAL_ID_LIMIT = 200;

    /** 外部版の上限の文字数（{@code external_record.external_version VARCHAR(100)}）。 */
    private static final int EXTERNAL_VERSION_LIMIT = 100;

    private IngestFileCommandAssembler() {}

    /**
     * 1 ファイルのバイト列から取込のコマンドを作る。
     *
     * @param dataSourceId 情報源 ID
     * @param kind 取込ファイルの種別
     * @param file ファイル全体の原本のバイト列（BOM を含むまま持たせる）
     */
    public static IngestFileCommand assemble(UUID dataSourceId, CsvFileKind kind, byte[] file) {
        CsvRecords records = CsvRecords.parse(file);
        int receivedCount = records.dataLineCount();
        return switch (CsvHeader.check(records, kind)) {
            case CsvHeader.FileError(FormatDefect defect, List<String> columns) ->
                new IngestFileCommand(
                        dataSourceId,
                        file,
                        receivedCount,
                        List.of(),
                        List.of(),
                        new UnreadablePart(records.firstDataLineNumber(), receivedCount, defect, columns, null));
            case CsvHeader.Columns(Map<String, Integer> positions) -> readRows(dataSourceId, file, records, positions);
        };
    }

    private static IngestFileCommand readRows(
            UUID dataSourceId, byte[] file, CsvRecords records, Map<String, Integer> positions) {
        List<IngestedRow> rows = new ArrayList<>();
        List<RejectedRow> rejectedRows = new ArrayList<>();
        UnreadablePart unreadable = null;
        for (CsvRow row : records.rows()) {
            KeyColumns keys = new KeyColumns(row.values(), positions);
            RejectedRow rejected = keys.validate(row);
            if (rejected == null) {
                rows.add(keys.toIngestedRow(row));
            } else {
                rejectedRows.add(rejected);
            }
        }
        for (CsvRowError error : records.errors()) {
            if (error.untilEndOfFile()) {
                unreadable = new UnreadablePart(
                        error.lineNumber(), error.range().lineCount(), error.defect(), List.of(), utf8(error.raw()));
            } else {
                rejectedRows.add(new RejectedRow(
                        error.lineNumber(),
                        error.lastLineNumber(),
                        error.defect(),
                        detailsOf(error, records.header().size()),
                        utf8(error.raw())));
            }
        }
        rejectedRows.sort(Comparator.comparingInt(RejectedRow::lineNumber));
        return new IngestFileCommand(dataSourceId, file, records.dataLineCount(), rows, rejectedRows, unreadable);
    }

    /** 読めなかった行の注記の詳細。列数の違う行は、ヘッダーとその行の列数（解析が読んだ値の数）を書く。 */
    private static List<String> detailsOf(CsvRowError error, int headerColumns) {
        if (error.columnCount() == null) {
            return List.of();
        }
        return List.of("ヘッダー " + headerColumns + " 列", "この行 " + error.columnCount() + " 列");
    }

    private static byte[] utf8(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    /** キーの列の検証の誤り（形式の誤りと注記の詳細）。 */
    private record Violation(FormatDefect defect, List<String> details) {}

    /** 読めた行のキーの列の値（加工しない）。 */
    private record KeyColumns(String externalId, String externalVersion, String acquiredAt) {

        KeyColumns(List<String> values, Map<String, Integer> positions) {
            this(
                    values.get(positions.get(EXTERNAL_ID)),
                    values.get(positions.get(EXTERNAL_VERSION)),
                    values.get(positions.get(ACQUIRED_AT)));
        }

        /** キーの列を列の順に検証し、最初の誤りの行の誤りを返す。誤りがなければ {@code null}。 */
        RejectedRow validate(CsvRow row) {
            Violation violation = textViolation(EXTERNAL_ID, externalId, EXTERNAL_ID_LIMIT);
            if (violation == null) {
                violation = textViolation(EXTERNAL_VERSION, externalVersion, EXTERNAL_VERSION_LIMIT);
            }
            if (violation == null) {
                violation = timeViolation(ACQUIRED_AT, acquiredAt);
            }
            return violation == null
                    ? null
                    : new RejectedRow(
                            row.lineNumber(),
                            row.lastLineNumber(),
                            violation.defect(),
                            violation.details(),
                            utf8(row.raw()));
        }

        IngestedRow toIngestedRow(CsvRow row) {
            return new IngestedRow(
                    row.lineNumber(),
                    row.lastLineNumber(),
                    externalId,
                    externalVersion,
                    parseTime(acquiredAt),
                    utf8(row.raw()));
        }

        /** 文字の列の誤り（空か、上限の文字数（コードポイント）より長い）。誤りがなければ {@code null}。 */
        private static Violation textViolation(String column, String value, int limit) {
            if (value.isBlank()) {
                return new Violation(FormatDefect.EMPTY_VALUE, List.of(column));
            }
            if (value.codePointCount(0, value.length()) > limit) {
                return new Violation(FormatDefect.TOO_LONG, List.of(column, limit + " 文字まで"));
            }
            return null;
        }

        /** 時刻の列の誤り（空か、書式の誤り）。誤りがなければ {@code null}。 */
        private static Violation timeViolation(String column, String value) {
            if (value.isBlank()) {
                return new Violation(FormatDefect.EMPTY_VALUE, List.of(column));
            }
            return parseTime(value) == null ? new Violation(FormatDefect.INVALID_TIME, List.of(column)) : null;
        }

        /**
         * オフセット付きの ISO 8601 の時刻（{@code Z} を含む。秒と小数は省略可）を読み、マイクロ秒に切り捨てる。読めなければ {@code null}
         * （オフセットのない値は時差を推測しない。OQ-02 の (c)）。
         */
        private static Instant parseTime(String value) {
            try {
                return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .toInstant()
                        .truncatedTo(ChronoUnit.MICROS);
            } catch (DateTimeParseException _) {
                return null;
            }
        }
    }
}
