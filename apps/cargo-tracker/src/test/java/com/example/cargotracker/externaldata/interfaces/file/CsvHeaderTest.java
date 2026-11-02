package com.example.cargotracker.externaldata.interfaces.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CSV のヘッダーの検証（Bolt 39 の行 12）。 */
class CsvHeaderTest {

    @Test
    void 航海のヘッダーに必要な列がそろえば列名から位置への写しを返す() {
        CsvHeader header = CsvHeader.check(
                List.of("voyage_number", "external_id", "external_version", "acquired_at"), CsvFileKind.VOYAGE);

        assertThat(header)
                .isEqualTo(new CsvHeader.Columns(
                        Map.of("voyage_number", 0, "external_id", 1, "external_version", 2, "acquired_at", 3)));
    }

    @Test
    void 主要実績のヘッダーは追跡番号の列もそろえば写しを返す() {
        CsvHeader header = CsvHeader.check(
                List.of("external_id", "external_version", "acquired_at", "tracking_number"), CsvFileKind.MILESTONE);

        assertThat(header).isInstanceOf(CsvHeader.Columns.class);
    }

    @Test
    void 必要な列がなければファイルの誤りにする() {
        assertThat(CsvHeader.check(List.of("external_id", "external_version"), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(CsvHeader.Reason.MISSING_COLUMN, List.of("acquired_at")));
    }

    @Test
    void 主要実績のファイルに追跡番号の列がなければファイルの誤りにする() {
        assertThat(CsvHeader.check(List.of("external_id", "external_version", "acquired_at"), CsvFileKind.MILESTONE))
                .isEqualTo(new CsvHeader.FileError(CsvHeader.Reason.MISSING_COLUMN, List.of("tracking_number")));
    }

    @Test
    void ヘッダーの行がなければ必要な列がすべてない誤りにする() {
        assertThat(CsvHeader.check(List.of(), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(
                        CsvHeader.Reason.MISSING_COLUMN, List.of("external_id", "external_version", "acquired_at")));
    }

    @Test
    void 同じ名前の列が重なればファイルの誤りにする() {
        assertThat(CsvHeader.check(
                        List.of("external_id", "external_version", "acquired_at", "external_id"), CsvFileKind.VOYAGE))
                .isEqualTo(new CsvHeader.FileError(CsvHeader.Reason.DUPLICATE_COLUMN, List.of("external_id")));
    }
}
