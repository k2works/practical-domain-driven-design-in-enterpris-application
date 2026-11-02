package com.example.cargotracker.externaldata.interfaces.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 仮の CSV（RFC 4180）の解析（Bolt 39 の行 9〜11）。 */
class CsvRecordsTest {

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static CsvRecords parse(String text) {
        return CsvRecords.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    private static CsvRow row(int lineNumber, String... values) {
        return new CsvRow(lineNumber, List.of(values));
    }

    @Test
    void カンマで区切った行を値の並びにする() {
        CsvRecords records = parse("external_id,external_version\nV-1,1\nV-2,3\n");

        assertThat(records.rows())
                .containsExactly(row(1, "external_id", "external_version"), row(2, "V-1", "1"), row(3, "V-2", "3"));
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void 引用符で囲んだ値のカンマと改行と二重の引用符を1つの値として読む() {
        CsvRecords records = parse("a,b,c\n\"x, y\",\"line1\nline2\",\"say \"\"hi\"\"\"\n1,,\"\"\n");

        assertThat(records.rows())
                .containsExactly(
                        row(1, "a", "b", "c"), row(2, "x, y", "line1\nline2", "say \"hi\""), row(4, "1", "", ""));
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void BOM付きでCRLFで最後の行に改行がなくても同じ行と値になる() {
        CsvRecords plain = parse("a,b\n1,\"2\r\n3\"\n4,5\n");
        CsvRecords windows = parse(BOM + "a,b\r\n1,\"2\r\n3\"\r\n4,5");

        assertThat(windows).isEqualTo(plain);
        assertThat(windows.rows()).containsExactly(row(1, "a", "b"), row(2, "1", "2\r\n3"), row(4, "4", "5"));
    }

    @Test
    void 空のファイルは行も誤りもない() {
        assertThat(parse("")).isEqualTo(new CsvRecords(List.of(), List.of()));
    }

    @Test
    void 列数の違う行は行番号と理由の付いた誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,2,3\n4\n5,6\n");

        assertThat(records.rows()).containsExactly(row(1, "a", "b"), row(4, "5", "6"));
        assertThat(records.errors())
                .containsExactly(
                        new CsvRowError(2, CsvRowError.Reason.COLUMN_COUNT_MISMATCH),
                        new CsvRowError(3, CsvRowError.Reason.COLUMN_COUNT_MISMATCH));
    }

    @Test
    void 閉じた引用符の後に文字がある行は誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n\"1\"x,2\n3,4\n");

        assertThat(records.rows()).containsExactly(row(1, "a", "b"), row(3, "3", "4"));
        assertThat(records.errors()).containsExactly(new CsvRowError(2, CsvRowError.Reason.TEXT_AFTER_CLOSING_QUOTE));
    }

    @Test
    void 引用符で囲まない値の中の引用符は行の誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,x\"y\n3,4\n");

        assertThat(records.rows()).containsExactly(row(1, "a", "b"), row(3, "3", "4"));
        assertThat(records.errors()).containsExactly(new CsvRowError(2, CsvRowError.Reason.BARE_QUOTE));
    }

    @Test
    void 引用符の閉じ忘れは誤りにしてそれより前の行は読む() {
        CsvRecords records = parse("a,b\n1,2\n\"3,4\n5,6\n");

        assertThat(records.rows()).containsExactly(row(1, "a", "b"), row(2, "1", "2"));
        assertThat(records.errors()).containsExactly(new CsvRowError(3, CsvRowError.Reason.UNCLOSED_QUOTE));
    }
}
