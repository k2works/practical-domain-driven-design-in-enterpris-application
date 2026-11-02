package com.example.cargotracker.externaldata.interfaces.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 仮の CSV（RFC 4180）の解析（Bolt 39 の行 9〜11 と、開発レビューで決めたヘッダー・空の行・誤りの範囲）。 */
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
    void 最初の行をヘッダーにしカンマで区切った行を値の並びにする() {
        CsvRecords records = parse("external_id,external_version\nV-1,1\nV-2,3\n");

        assertThat(records.header()).containsExactly("external_id", "external_version");
        assertThat(records.rows()).containsExactly(row(2, "V-1", "1"), row(3, "V-2", "3"));
        assertThat(records.errors()).isEmpty();
        assertThat(records.headerUnreadable()).isFalse();
    }

    @Test
    void 引用符で囲んだ値のカンマと改行と二重の引用符を1つの値として読む() {
        CsvRecords records = parse("a,b,c\n\"x, y\",\"line1\nline2\",\"say \"\"hi\"\"\"\n1,,\"\"\n");

        assertThat(records.rows()).containsExactly(row(2, "x, y", "line1\nline2", "say \"hi\""), row(4, "1", "", ""));
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void 引用符4つの値は引用符1つになる() {
        assertThat(parse("a\n\"\"\"\"\n").rows()).containsExactly(row(2, "\""));
    }

    @Test
    void BOM付きでCRLFで最後の行に改行がなくても同じ行と値になる() {
        CsvRecords plain = parse("a,b\n1,\"2\r\n3\"\n4,5\n");
        CsvRecords windows = parse(BOM + "a,b\r\n1,\"2\r\n3\"\r\n4,5");

        assertThat(windows).isEqualTo(plain);
        assertThat(windows.header()).containsExactly("a", "b");
        assertThat(windows.rows()).containsExactly(row(2, "1", "2\r\n3"), row(4, "4", "5"));
    }

    @Test
    void 単独のCRは改行でなく値の中身として読む() {
        assertThat(parse("a,b\n1\r2,3\n4,5\r").rows()).containsExactly(row(2, "1\r2", "3"), row(3, "4", "5\r"));
    }

    @Test
    void 空の行は途中も末尾も読み飛ばし行番号は物理行のまま保つ() {
        CsvRecords records = parse("\na,b\n\n1,2\r\n\r\n\n3,4\n\n");

        assertThat(records.header()).containsExactly("a", "b");
        assertThat(records.rows()).containsExactly(row(4, "1", "2"), row(7, "3", "4"));
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void 空のファイルはヘッダーも行も誤りもない() {
        CsvRecords records = parse("");

        assertThat(records).isEqualTo(new CsvRecords(List.of(), List.of(), List.of()));
        assertThat(records.headerUnreadable()).isFalse();
    }

    @Test
    void ヘッダーだけのファイルは行も誤りもない() {
        CsvRecords records = parse("a,b\n");

        assertThat(records.header()).containsExactly("a", "b");
        assertThat(records.rows()).isEmpty();
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void 列が1つのCSVも読む() {
        CsvRecords records = parse("external_id\nV-1\n\nV-2");

        assertThat(records.header()).containsExactly("external_id");
        assertThat(records.rows()).containsExactly(row(2, "V-1"), row(4, "V-2"));
    }

    @Test
    void 列数の違う行はヘッダーと比べて誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,2,3\n4\n5,6\n");

        assertThat(records.rows()).containsExactly(row(4, "5", "6"));
        assertThat(records.errors())
                .containsExactly(
                        new CsvRowError(2, CsvRowError.Reason.COLUMN_COUNT_MISMATCH),
                        new CsvRowError(3, CsvRowError.Reason.COLUMN_COUNT_MISMATCH));
    }

    @Test
    void 複数の物理行にまたがる行の誤りは始まりと終わりの行を持つ() {
        CsvRecords records = parse("a,b\n\"x\ny\"\n3,4\n");

        assertThat(records.rows()).containsExactly(row(4, "3", "4"));
        assertThat(records.errors()).containsExactly(new CsvRowError(2, 3, CsvRowError.Reason.COLUMN_COUNT_MISMATCH));
    }

    @Test
    void 閉じた引用符の後に文字がある行は誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n\"1\"x,2\n3,4\n");

        assertThat(records.rows()).containsExactly(row(3, "3", "4"));
        assertThat(records.errors()).containsExactly(new CsvRowError(2, CsvRowError.Reason.TEXT_AFTER_CLOSING_QUOTE));
    }

    @Test
    void 引用符で囲まない値の中の引用符は行の誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,x\"y\n3,4\n");

        assertThat(records.rows()).containsExactly(row(3, "3", "4"));
        assertThat(records.errors()).containsExactly(new CsvRowError(2, CsvRowError.Reason.BARE_QUOTE));
    }

    @Test
    void 誤りの行の後ろに複数の物理行にまたがる引用符の値があってもその行の終わりから立ち直る() {
        CsvRecords bare = parse("a,b,c\n1,x\"y,\"multi\nline\"\n3,4,5\n");
        CsvRecords afterClosing = parse("a,b\n\"1\"x,\"p\nq\"\n3,4\n");

        assertThat(bare.rows()).containsExactly(row(4, "3", "4", "5"));
        assertThat(bare.errors()).containsExactly(new CsvRowError(2, 3, CsvRowError.Reason.BARE_QUOTE));
        assertThat(afterClosing.rows()).containsExactly(row(4, "3", "4"));
        assertThat(afterClosing.errors())
                .containsExactly(new CsvRowError(2, 3, CsvRowError.Reason.TEXT_AFTER_CLOSING_QUOTE));
    }

    @Test
    void 引用符の閉じ忘れはファイルの終わりの行までの誤りにしてそれより前の行は読む() {
        CsvRecords records = parse("a,b\n1,2\n\"3,4\n5,6\n");
        CsvRecords noLastNewline = parse("a,b\n\"x\ny");

        assertThat(records.rows()).containsExactly(row(2, "1", "2"));
        assertThat(records.errors()).containsExactly(new CsvRowError(3, 4, CsvRowError.Reason.UNCLOSED_QUOTE));
        assertThat(noLastNewline.errors()).containsExactly(new CsvRowError(2, 3, CsvRowError.Reason.UNCLOSED_QUOTE));
    }

    @Test
    void ヘッダーの行が読めなければヘッダーを読めないとしてほかの行を読まない() {
        CsvRecords records = parse("a,b\"c\n1,2\n");

        assertThat(records.headerUnreadable()).isTrue();
        assertThat(records.header()).isEmpty();
        assertThat(records.rows()).isEmpty();
        assertThat(records.errors()).containsExactly(new CsvRowError(1, CsvRowError.Reason.BARE_QUOTE));
    }
}
