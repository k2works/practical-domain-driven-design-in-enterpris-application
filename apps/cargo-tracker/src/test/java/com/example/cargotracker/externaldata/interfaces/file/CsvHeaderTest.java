package com.example.cargotracker.externaldata.interfaces.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CSV のヘッダーの検証（Bolt 39 の行 12。Bolt 39b2 の行 12 の文字コードの誤り。Bolt 39b2b の行 15 の値の列）。 */
class CsvHeaderTest {

    private static final List<String> MILESTONE_COLUMNS = List.of(
            "external_id",
            "external_version",
            "acquired_at",
            "tracking_number",
            "kind",
            "location_unlocode",
            "occurred_at");

    @Test
    void 航海のヘッダーに必要な列がそろえば列名から位置への写しを返す() {
        CsvHeader header = CsvHeader.check(
                List.of("voyage_number", "external_id", "external_version", "acquired_at", "port_calls"),
                CsvFileKind.VOYAGE);

        assertThat(header)
                .isEqualTo(new CsvHeader.Columns(Map.of(
                        "voyage_number",
                        0,
                        "external_id",
                        1,
                        "external_version",
                        2,
                        "acquired_at",
                        3,
                        "port_calls",
                        4)));
    }

    @Test
    void 主要実績のヘッダーは追跡番号と値の列もそろえば写しを返す() {
        CsvHeader header = CsvHeader.check(MILESTONE_COLUMNS, CsvFileKind.MILESTONE);

        assertThat(header).isInstanceOf(CsvHeader.Columns.class);
    }

    @Test
    void 必要な列がなければファイルの誤りにする() {
        assertThat(CsvHeader.check(List.of("external_id", "external_version"), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(FormatDefect.MISSING_COLUMN, List.of("acquired_at", "port_calls")));
    }

    @Test
    void 主要実績のファイルに追跡番号と値の列がなければファイルの誤りにする() {
        assertThat(CsvHeader.check(List.of("external_id", "external_version", "acquired_at"), CsvFileKind.MILESTONE))
                .isEqualTo(new CsvHeader.FileError(
                        FormatDefect.MISSING_COLUMN,
                        List.of("tracking_number", "kind", "location_unlocode", "occurred_at")));
    }

    @Test
    void 行15_航海のヘッダーに寄港の列がなければその列名を持つファイルの誤りにする() {
        assertThat(CsvHeader.check(List.of("external_id", "external_version", "acquired_at"), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(FormatDefect.MISSING_COLUMN, List.of("port_calls")));
    }

    @Test
    void 行15_主要実績のヘッダーに種類か場所か発生時刻の列がなければその列名を持つファイルの誤りにする() {
        for (String missing : List.of("kind", "location_unlocode", "occurred_at")) {
            List<String> columns = MILESTONE_COLUMNS.stream()
                    .filter(column -> !column.equals(missing))
                    .toList();

            assertThat(CsvHeader.check(columns, CsvFileKind.MILESTONE))
                    .as("%s のないヘッダー", missing)
                    .isEqualTo(new CsvHeader.FileError(FormatDefect.MISSING_COLUMN, List.of(missing)));
        }
    }

    @Test
    void ヘッダーの行がなければ必要な列がすべてない誤りにする() {
        assertThat(CsvHeader.check(List.of(), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(
                        FormatDefect.MISSING_COLUMN,
                        List.of("external_id", "external_version", "acquired_at", "port_calls")));
    }

    @Test
    void 読んだCSVのヘッダーを検証する() {
        CsvRecords records = CsvRecords.parse(
                "external_id,external_version,acquired_at,port_calls\nV-1,1,2026-11-02T00:00:00Z,JPTYO;;2026-11-03T00:00:00Z|USLAX;2026-11-20T00:00:00Z;\n"
                        .getBytes(StandardCharsets.UTF_8));

        assertThat(CsvHeader.check(records, CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.Columns(
                        Map.of("external_id", 0, "external_version", 1, "acquired_at", 2, "port_calls", 3)));
    }

    @Test
    void ヘッダーの行が読めなければファイルの誤りにする() {
        CsvRecords records =
                CsvRecords.parse("external_id,\"external_version\nV-1,1\n".getBytes(StandardCharsets.UTF_8));

        assertThat(CsvHeader.check(records, CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(FormatDefect.UNREADABLE_HEADER, List.of()));
    }

    @Test
    void 行12_文字コードの誤りのファイルはヘッダーを読む前にファイルの誤りにする() {
        CsvRecords records = CsvRecords.parse("external_id,external_version,acquired_at\n航海,1,2026-11-03T00:00:00Z\n"
                .getBytes(Charset.forName("Shift_JIS")));

        assertThat(CsvHeader.check(records, CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(FormatDefect.UNDECODABLE, List.of()));
    }

    @Test
    void 同じ名前の列が重なればファイルの誤りにする() {
        assertThat(CsvHeader.check(
                        List.of("external_id", "external_version", "acquired_at", "external_id"), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(FormatDefect.DUPLICATE_COLUMN, List.of("external_id")));
    }

    @Test
    void 行1_ファイルの誤りにならない形式の誤りはファイルの誤りにできない() {
        List<String> none = List.of();

        for (FormatDefect defect : List.of(
                FormatDefect.UNCLOSED_QUOTE,
                FormatDefect.TEXT_AFTER_CLOSING_QUOTE,
                FormatDefect.BARE_QUOTE,
                FormatDefect.COLUMN_COUNT_MISMATCH,
                FormatDefect.EMPTY_VALUE)) {
            assertThatThrownBy(() -> new CsvHeader.FileError(defect, none))
                    .as("ファイルの誤りの %s", defect)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
