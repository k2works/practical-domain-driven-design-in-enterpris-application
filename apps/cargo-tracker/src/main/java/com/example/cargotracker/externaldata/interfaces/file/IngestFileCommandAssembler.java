package com.example.cargotracker.externaldata.interfaces.file;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.RecordValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValuesRejected;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValuesRejectionReason;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 取込の組み立て。CSV の解析（{@link CsvRecords}）とヘッダーの検証（{@link CsvHeader}）の結果から、値だけの取込のコマンド
 * （{@link IngestFileCommand}）を作る関数（UC-15 2a。Bolt 39b2。値の列は Bolt 39b2b）。Spring の部品にせず（画面の層がつなぐ。39c）、
 * リポジトリを使わない。
 *
 * <p>ファイルの誤り（文字コード・ヘッダーが読めない・必要な列がない・同じ名前の列）は行を読まず、取り込めなかった部分（データ行があれば
 * 最初のデータ行から受け取った件数のすべて、なければ始まりなしで 0）にする。引用符が閉じないままファイルの終わりまで飲み込んだ範囲は原文付きの
 * 取り込めなかった部分に、ほかの読めなかった行は行の誤りにする。読めた行はキーの列（外部 ID・外部版・取得時刻）を先に、次に値の列を必要な列の
 * 順（{@link CsvFileKind}）に検証し、最初の 1 つの誤りを行の誤りにする。空（空白だけを含む）・長すぎる（表の列の上限）・時刻の書式の誤りは
 * 組み立てが見つけ、場所・種類・追跡番号・寄港の形の誤りは取込の値の型の拒否を形式の誤りに写す（寄港の列と航海番号は型が返す理由
 * （{@link VoyageValuesRejected}）をそのまま詳細にし、型の外で理由を推し量らない）。正しい行は取込の値（航海の値か主要実績の値。
 * 航海番号は外部 ID の列の値）を持つ取り込む行にする。値は加工せずに渡し（前後の空白・小文字を直さない）、原文は UTF-8 のバイト列にする。
 */
public final class IngestFileCommandAssembler {

    private static final String EXTERNAL_ID = "external_id";
    private static final String EXTERNAL_VERSION = "external_version";
    private static final String ACQUIRED_AT = "acquired_at";
    private static final String PORT_CALLS = "port_calls";
    private static final String TRACKING_NUMBER = "tracking_number";
    private static final String KIND = "kind";
    private static final String LOCATION = "location_unlocode";
    private static final String OCCURRED_AT = "occurred_at";

    /** 外部 ID の上限の文字数（{@code external_record.external_id VARCHAR(200)}）。 */
    private static final int EXTERNAL_ID_LIMIT = 200;

    /** 外部版の上限の文字数（{@code external_record.external_version VARCHAR(100)}）。 */
    private static final int EXTERNAL_VERSION_LIMIT = 100;

    /** 寄港の区切りと、寄港の中の港・到着・出発の区切り（仮の形式。OQ-02）。 */
    private static final String PORT_CALL_SEPARATOR = "\\|";

    private static final String FIELD_SEPARATOR = ";";
    private static final int PORT_CALL_FIELDS = 3;

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
            case CsvHeader.Columns(Map<String, Integer> positions) ->
                readRows(dataSourceId, kind, file, records, positions);
        };
    }

    private static IngestFileCommand readRows(
            UUID dataSourceId, CsvFileKind kind, byte[] file, CsvRecords records, Map<String, Integer> positions) {
        List<IngestedRow> rows = new ArrayList<>();
        List<RejectedRow> rejectedRows = new ArrayList<>();
        UnreadablePart unreadable = null;
        for (CsvRow row : records.rows()) {
            Read<IngestedRow> read = readRow(kind, row, new RowColumns(row.values(), positions));
            if (read.hasViolation()) {
                Violation violation = read.violation();
                rejectedRows.add(new RejectedRow(
                        row.lineNumber(),
                        row.lastLineNumber(),
                        violation.defect(),
                        violation.details(),
                        utf8(row.raw())));
            } else {
                rows.add(read.value());
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

    /** 読めた 1 行をキーの列 → 値の列の順に検証し、取り込む行か最初の誤りにする。 */
    private static Read<IngestedRow> readRow(CsvFileKind kind, CsvRow row, RowColumns columns) {
        Read<String> externalId = text(EXTERNAL_ID, columns.get(EXTERNAL_ID), EXTERNAL_ID_LIMIT);
        if (externalId.hasViolation()) {
            return externalId.failure();
        }
        Read<String> externalVersion = text(EXTERNAL_VERSION, columns.get(EXTERNAL_VERSION), EXTERNAL_VERSION_LIMIT);
        if (externalVersion.hasViolation()) {
            return externalVersion.failure();
        }
        Read<Instant> acquiredAt = time(ACQUIRED_AT, columns.get(ACQUIRED_AT));
        if (acquiredAt.hasViolation()) {
            return acquiredAt.failure();
        }
        Read<RecordValues> values =
                switch (kind) {
                    case VOYAGE -> voyageValues(externalId.value(), columns);
                    case MILESTONE -> milestoneValues(columns);
                };
        if (values.hasViolation()) {
            return values.failure();
        }
        return Read.of(new IngestedRow(
                row.lineNumber(),
                row.lastLineNumber(),
                externalId.value(),
                externalVersion.value(),
                acquiredAt.value(),
                values.value(),
                utf8(row.raw())));
    }

    /**
     * 航海の値。航海番号（外部 ID の列の値）の長さ、寄港の列（寄港の順に区切り → 港 → 到着 → 出発 → 到着と出発の組み合わせ）、寄港の数と重複
     * （寄港をすべて読んだ後）の順に確かめる。
     */
    private static Read<RecordValues> voyageValues(String voyageNumber, RowColumns columns) {
        try {
            VoyageValues.requireVoyageNumber(voyageNumber);
        } catch (VoyageValuesRejected rejected) {
            return Read.failed(voyageNumberViolation(rejected));
        }
        String portCalls = columns.get(PORT_CALLS);
        if (portCalls.isBlank()) {
            return Read.failed(FormatDefect.EMPTY_VALUE, PORT_CALLS);
        }
        List<PortCallValue> calls = new ArrayList<>();
        String[] texts = portCalls.split(PORT_CALL_SEPARATOR, -1);
        for (int i = 0; i < texts.length; i++) {
            Read<PortCallValue> call = portCall(i + 1, texts[i]);
            if (call.hasViolation()) {
                return call.failure();
            }
            calls.add(call.value());
        }
        try {
            return Read.of(new VoyageValues(voyageNumber, calls));
        } catch (VoyageValuesRejected rejected) {
            return Read.failed(portCallsViolation(rejected));
        }
    }

    /**
     * 寄港の値（{@code UN/LOCODE;到着;出発}。到着・出発は空なら無し）。注記の詳細に寄港の番号を書き、寄港の値の拒否はその番号の寄港の
     * 理由にする。
     */
    private static Read<PortCallValue> portCall(int number, String text) {
        String position = number + " つ目の寄港";
        String[] fields = text.split(FIELD_SEPARATOR, -1);
        if (fields.length != PORT_CALL_FIELDS) {
            return Read.failed(portCallsViolation(
                    new VoyageValuesRejected(VoyageValuesRejectionReason.UNSPLITTABLE_PORT_CALL).at(number)));
        }
        Read<Location> port = attempt(
                () -> new Location(fields[0]), Violation.of(FormatDefect.INVALID_LOCATION, PORT_CALLS, position));
        if (port.hasViolation()) {
            return port.failure();
        }
        Read<UtcInstant> arrival = optionalTime(fields[1], position, "到着");
        if (arrival.hasViolation()) {
            return arrival.failure();
        }
        Read<UtcInstant> departure = optionalTime(fields[2], position, "出発");
        if (departure.hasViolation()) {
            return departure.failure();
        }
        try {
            return Read.of(new PortCallValue(port.value(), arrival.value(), departure.value()));
        } catch (VoyageValuesRejected rejected) {
            return Read.failed(portCallsViolation(rejected.at(number)));
        }
    }

    /** 寄港の列の拒否を、型の理由の言葉をそのまま詳細にした寄港の列の形の誤りにする。 */
    private static Violation portCallsViolation(VoyageValuesRejected rejected) {
        return Violation.of(FormatDefect.INVALID_PORT_CALLS, PORT_CALLS, rejected.detail());
    }

    /**
     * 航海番号の拒否。長すぎる航海番号は外部 ID の列の長すぎる値として上限を書く（キーの列の長さの誤りと同じ言葉）。空の航海番号は外部 ID の
     * 列の空の値（キーの列で先に見つかるので、ここには来ない）。
     */
    private static Violation voyageNumberViolation(VoyageValuesRejected rejected) {
        return switch (rejected.reason()) {
            case VOYAGE_NUMBER_TOO_LONG ->
                Violation.of(FormatDefect.TOO_LONG, EXTERNAL_ID, VoyageValues.VOYAGE_NUMBER_MAX_LENGTH + " 文字まで");
            case VOYAGE_NUMBER_BLANK -> Violation.of(FormatDefect.EMPTY_VALUE, EXTERNAL_ID);
            default -> throw new IllegalStateException("航海番号の拒否ではない理由: " + rejected.reason(), rejected);
        };
    }

    /** 寄港の到着か出発（空なら無し）。 */
    private static Read<UtcInstant> optionalTime(String text, String position, String which) {
        if (text.isEmpty()) {
            return Read.of(null);
        }
        Instant instant = parseTime(text);
        return instant == null
                ? Read.failed(FormatDefect.INVALID_TIME, PORT_CALLS, position, which)
                : Read.of(new UtcInstant(instant));
    }

    /** 主要実績の値。追跡番号 → 種類 → 場所 → 発生時刻の順（必要な列の順）に確かめる。 */
    private static Read<RecordValues> milestoneValues(RowColumns columns) {
        Read<TrackingNumberValue> trackingNumber =
                parsed(TRACKING_NUMBER, columns, TrackingNumberValue::new, FormatDefect.INVALID_TRACKING_NUMBER);
        if (trackingNumber.hasViolation()) {
            return trackingNumber.failure();
        }
        Read<MilestoneKindValue> kind = parsed(KIND, columns, MilestoneKindValue::valueOf, FormatDefect.INVALID_KIND);
        if (kind.hasViolation()) {
            return kind.failure();
        }
        Read<Location> location = parsed(LOCATION, columns, Location::new, FormatDefect.INVALID_LOCATION);
        if (location.hasViolation()) {
            return location.failure();
        }
        Read<Instant> occurredAt = time(OCCURRED_AT, columns.get(OCCURRED_AT));
        if (occurredAt.hasViolation()) {
            return occurredAt.failure();
        }
        return Read.of(new MilestoneValues(
                trackingNumber.value(), kind.value(), location.value(), new UtcInstant(occurredAt.value())));
    }

    /** 値の型を作る列（空なら空の値、型が拒めばその形式の誤り。詳細は列名）。 */
    private static <T> Read<T> parsed(
            String column, RowColumns columns, Function<String, T> constructor, FormatDefect defect) {
        String value = columns.get(column);
        if (value.isBlank()) {
            return Read.failed(FormatDefect.EMPTY_VALUE, column);
        }
        return attempt(() -> constructor.apply(value), Violation.of(defect, column));
    }

    /** 値の型を作り、型の拒否（{@link IllegalArgumentException}）をその誤りに写す。 */
    private static <T> Read<T> attempt(Supplier<T> constructor, Violation violation) {
        try {
            return Read.of(constructor.get());
        } catch (IllegalArgumentException _) {
            return Read.failed(violation);
        }
    }

    /** 文字の列（空か、上限の文字数（コードポイント）より長ければ誤り）。 */
    private static Read<String> text(String column, String value, int limit) {
        if (value.isBlank()) {
            return Read.failed(FormatDefect.EMPTY_VALUE, column);
        }
        if (value.codePointCount(0, value.length()) > limit) {
            return Read.failed(FormatDefect.TOO_LONG, column, limit + " 文字まで");
        }
        return Read.of(value);
    }

    /** 時刻の列（空か、書式の誤りなら誤り）。 */
    private static Read<Instant> time(String column, String value) {
        if (value.isBlank()) {
            return Read.failed(FormatDefect.EMPTY_VALUE, column);
        }
        Instant instant = parseTime(value);
        return instant == null ? Read.failed(FormatDefect.INVALID_TIME, column) : Read.of(instant);
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

    /** 読めた行の列の値（加工しない）。 */
    private record RowColumns(List<String> values, Map<String, Integer> positions) {

        String get(String column) {
            return values.get(positions.get(column));
        }
    }

    /** 列の検証の誤り（形式の誤りと注記の詳細）。 */
    private record Violation(FormatDefect defect, List<String> details) {

        static Violation of(FormatDefect defect, String... details) {
            return new Violation(defect, List.of(details));
        }
    }

    /**
     * 列の読み取りの結果。値か誤りのどちらか（誤りがなければ値。寄港の無い到着・出発は値が {@code null}）。
     *
     * @param value 読んだ値
     * @param violation 誤り（なければ {@code null}）
     */
    private record Read<T>(T value, Violation violation) {

        static <T> Read<T> of(T value) {
            return new Read<>(value, null);
        }

        static <T> Read<T> failed(Violation violation) {
            return new Read<>(null, violation);
        }

        static <T> Read<T> failed(FormatDefect defect, String... details) {
            return failed(Violation.of(defect, details));
        }

        boolean hasViolation() {
            return violation != null;
        }

        /** 誤りを別の値の型の結果として渡す。 */
        <U> Read<U> failure() {
            return failed(violation);
        }
    }
}
