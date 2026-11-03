package com.example.cargotracker.externaldata.interfaces.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 仮の CSV（RFC 4180）の解析（Bolt 39 の行 9〜11 と、開発レビューで決めたヘッダー・空の行・誤りの範囲。Bolt 39b2 の行 5b・6・6b・10〜12・15。
 * 厳密な UTF-8、終わりの物理行と原文、物理データ行の数）。
 */
class CsvRecordsTest {

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static CsvRecords parse(String text) {
        return CsvRecords.parse(text.getBytes(StandardCharsets.UTF_8));
    }

    private static CsvRow row(int lineNumber, String raw, String... values) {
        return row(lineNumber, lineNumber, raw, values);
    }

    private static CsvRow row(int lineNumber, int lastLineNumber, String raw, String... values) {
        return new CsvRow(lineNumber, lastLineNumber, List.of(values), raw);
    }

    private static CsvRowError error(int lineNumber, int lastLineNumber, FormatDefect defect, String raw) {
        return new CsvRowError(lineNumber, lastLineNumber, defect, raw, false);
    }

    private static CsvRowError untilEndOfFile(int lineNumber, int lastLineNumber, FormatDefect defect, String raw) {
        return new CsvRowError(lineNumber, lastLineNumber, defect, raw, true);
    }

    /** 行の結果と誤りの物理行の数の合計（受け取った件数と突き合わせる値）。 */
    private static int spannedLines(CsvRecords records) {
        return Stream.concat(
                        records.rows().stream().map(r -> r.lastLineNumber() - r.lineNumber() + 1),
                        records.errors().stream().map(e -> e.lastLineNumber() - e.lineNumber() + 1))
                .mapToInt(Integer::intValue)
                .sum();
    }

    @Test
    void 最初の行をヘッダーにしカンマで区切った行を値の並びにする() {
        CsvRecords records = parse("external_id,external_version\nV-1,1\nV-2,3\n");

        assertThat(records.header()).containsExactly("external_id", "external_version");
        assertThat(records.rows()).containsExactly(row(2, "V-1,1", "V-1", "1"), row(3, "V-2,3", "V-2", "3"));
        assertThat(records.errors()).isEmpty();
        assertThat(records.headerUnreadable()).isFalse();
        assertThat(records.undecodable()).isFalse();
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 引用符で囲んだ値のカンマと改行と二重の引用符を1つの値として読む() {
        CsvRecords records = parse("a,b,c\n\"x, y\",\"line1\nline2\",\"say \"\"hi\"\"\"\n1,,\"\"\n");

        assertThat(records.rows())
                .containsExactly(
                        row(2, 3, "\"x, y\",\"line1\nline2\",\"say \"\"hi\"\"\"", "x, y", "line1\nline2", "say \"hi\""),
                        row(4, "1,,\"\"", "1", "", ""));
        assertThat(records.errors()).isEmpty();
    }

    @Test
    void 引用符4つの値は引用符1つになる() {
        assertThat(parse("a\n\"\"\"\"\n").rows()).containsExactly(row(2, "\"\"\"\"", "\""));
    }

    @Test
    void BOM付きでCRLFで最後の行に改行がなくても同じ行と値になる() {
        CsvRecords plain = parse("a,b\n1,\"2\r\n3\"\n4,5\n");
        CsvRecords windows = parse(BOM + "a,b\r\n1,\"2\r\n3\"\r\n4,5");

        assertThat(windows).isEqualTo(plain);
        assertThat(windows.header()).containsExactly("a", "b");
        assertThat(windows.rows()).containsExactly(row(2, 3, "1,\"2\r\n3\"", "1", "2\r\n3"), row(4, "4,5", "4", "5"));
    }

    @Test
    void 単独のCRは改行でなく値の中身として読む() {
        assertThat(parse("a,b\n1\r2,3\n4,5\r").rows())
                .containsExactly(row(2, "1\r2,3", "1\r2", "3"), row(3, "4,5\r", "4", "5\r"));
    }

    @Test
    void 空の行は途中も末尾も読み飛ばし行番号は物理行のまま保つ() {
        CsvRecords records = parse("\na,b\n\n1,2\r\n\r\n\n3,4\n\n");

        assertThat(records.header()).containsExactly("a", "b");
        assertThat(records.rows()).containsExactly(row(4, "1,2", "1", "2"), row(7, "3,4", "3", "4"));
        assertThat(records.errors()).isEmpty();
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 空のファイルはヘッダーも行も誤りもない() {
        CsvRecords records = parse("");

        assertThat(records).isEqualTo(new CsvRecords(List.of(), List.of(), List.of(), false, 0));
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
        assertThat(records.rows()).containsExactly(row(2, "V-1", "V-1"), row(4, "V-2", "V-2"));
    }

    @Test
    void 列数の違う行はヘッダーと比べて誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,2,3\n4\n5,6\n");

        assertThat(records.rows()).containsExactly(row(4, "5,6", "5", "6"));
        assertThat(records.errors())
                .containsExactly(
                        error(2, 2, FormatDefect.COLUMN_COUNT_MISMATCH, "1,2,3"),
                        error(3, 3, FormatDefect.COLUMN_COUNT_MISMATCH, "4"));
    }

    @Test
    void 複数の物理行にまたがる行の誤りは始まりと終わりの行を持つ() {
        CsvRecords records = parse("a,b\n\"x\ny\"\n3,4\n");

        assertThat(records.rows()).containsExactly(row(4, "3,4", "3", "4"));
        assertThat(records.errors()).containsExactly(error(2, 3, FormatDefect.COLUMN_COUNT_MISMATCH, "\"x\ny\""));
    }

    @Test
    void 閉じた引用符の後に文字がある行は誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n\"1\"x,2\n3,4\n");

        assertThat(records.rows()).containsExactly(row(3, "3,4", "3", "4"));
        assertThat(records.errors()).containsExactly(error(2, 2, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "\"1\"x,2"));
    }

    @Test
    void 引用符で囲まない値の中の引用符は行の誤りにしてほかの行は読む() {
        CsvRecords records = parse("a,b\n1,x\"y\n3,4\n");

        assertThat(records.rows()).containsExactly(row(3, "3,4", "3", "4"));
        assertThat(records.errors()).containsExactly(error(2, 2, FormatDefect.BARE_QUOTE, "1,x\"y"));
    }

    @Test
    void 誤りの行の後ろに複数の物理行にまたがる引用符の値があってもその行の終わりから立ち直る() {
        CsvRecords bare = parse("a,b,c\n1,x\"y,\"multi\nline\"\n3,4,5\n");
        CsvRecords afterClosing = parse("a,b\n\"1\"x,\"p\nq\"\n3,4\n");

        assertThat(bare.rows()).containsExactly(row(4, "3,4,5", "3", "4", "5"));
        assertThat(bare.errors()).containsExactly(error(2, 3, FormatDefect.BARE_QUOTE, "1,x\"y,\"multi\nline\""));
        assertThat(afterClosing.rows()).containsExactly(row(4, "3,4", "3", "4"));
        assertThat(afterClosing.errors())
                .containsExactly(error(2, 3, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "\"1\"x,\"p\nq\""));
    }

    @Test
    void 引用符の閉じ忘れはファイルの終わりの行までの誤りにしてそれより前の行は読む() {
        CsvRecords records = parse("a,b\n1,2\n\"3,4\n5,6\n");
        CsvRecords noLastNewline = parse("a,b\n\"x\ny");

        assertThat(records.rows()).containsExactly(row(2, "1,2", "1", "2"));
        assertThat(records.errors()).containsExactly(untilEndOfFile(3, 4, FormatDefect.UNCLOSED_QUOTE, "\"3,4\n5,6"));
        assertThat(records.dataLineCount()).isEqualTo(3);
        assertThat(noLastNewline.errors()).containsExactly(untilEndOfFile(2, 3, FormatDefect.UNCLOSED_QUOTE, "\"x\ny"));
    }

    @Test
    void 引用符の閉じ忘れはファイルの終わりまで飲み込んだ印のない誤りにできない() {
        assertThatThrownBy(() -> new CsvRowError(2, 3, FormatDefect.UNCLOSED_QUOTE, "\"x\ny", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ヘッダーの行が読めなければヘッダーを読めないとしてほかの行を読まない() {
        CsvRecords records = parse("a,b\"c\n1,2\n\n3,4");

        assertThat(records.headerUnreadable()).isTrue();
        assertThat(records.header()).isEmpty();
        assertThat(records.rows()).isEmpty();
        assertThat(records.errors()).containsExactly(error(1, 1, FormatDefect.BARE_QUOTE, "a,b\"c"));
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行5b_閉じ忘れが最初のデータ行なら全データ行を1つの誤りにする() {
        CsvRecords records = parse("a,b\n\"1,2\n3,4\n");

        assertThat(records.rows()).isEmpty();
        assertThat(records.errors()).containsExactly(untilEndOfFile(2, 3, FormatDefect.UNCLOSED_QUOTE, "\"1,2\n3,4"));
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行5b_閉じ忘れが改行のない最後の行なら1行の誤りにする() {
        CsvRecords records = parse("a,b\n1,2\n\"3,4");

        assertThat(records.rows()).containsExactly(row(2, "1,2", "1", "2"));
        assertThat(records.errors()).containsExactly(untilEndOfFile(3, 3, FormatDefect.UNCLOSED_QUOTE, "\"3,4"));
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行5b_ヘッダーの閉じ忘れはヘッダーを読めず件数は最初のLFの後の空行を除いた物理行で数える() {
        CsvRecords records = parse("a,\"b\n1,2\n\n3,4\n");

        assertThat(records.headerUnreadable()).isTrue();
        assertThat(records.rows()).isEmpty();
        assertThat(records.errors())
                .containsExactly(untilEndOfFile(1, 4, FormatDefect.UNCLOSED_QUOTE, "a,\"b\n1,2\n\n3,4"));
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行6_閉じた引用符の後の文字の後に改行を含む値の行と空行があっても後の行を読む() {
        CsvRecords records = parse("a,b\n\"1\"x,2\n\"p\nq\",3\n\n4,5\n");

        assertThat(records.errors()).containsExactly(error(2, 2, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "\"1\"x,2"));
        assertThat(records.rows()).containsExactly(row(3, 4, "\"p\nq\",3", "p\nq", "3"), row(6, "4,5", "4", "5"));
        assertThat(records.dataLineCount()).isEqualTo(4);
    }

    @Test
    void 行6b_読み飛ばす途中で引用符が閉じないままファイルの終わりまで飲み込めば理由はそのままにファイルの終わりまでの誤りにする() {
        CsvRecords afterClosing = parse("a,b\n1,2\n\"3\"x,\"4\n5,6\n");
        CsvRecords bare = parse("a,b\n1,x\"y,\"z\n5,6");

        assertThat(afterClosing.rows()).containsExactly(row(2, "1,2", "1", "2"));
        assertThat(afterClosing.errors())
                .containsExactly(untilEndOfFile(3, 4, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "\"3\"x,\"4\n5,6"));
        assertThat(afterClosing.dataLineCount()).isEqualTo(3);
        assertThat(bare.rows()).isEmpty();
        assertThat(bare.errors()).containsExactly(untilEndOfFile(2, 3, FormatDefect.BARE_QUOTE, "1,x\"y,\"z\n5,6"));
        assertThat(bare.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行6b_ヘッダーで飲み込めばヘッダーを読めず件数は最初のLFの後の物理行で数える() {
        CsvRecords records = parse("\"a\"x,\"b\n1,2\n");

        assertThat(records.headerUnreadable()).isTrue();
        assertThat(records.errors())
                .containsExactly(untilEndOfFile(1, 2, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, "\"a\"x,\"b\n1,2"));
        assertThat(records.dataLineCount()).isEqualTo(1);
    }

    @Test
    void 行10_0バイトとBOMだけとヘッダーだけのファイルは件数0() {
        CsvRecords empty = CsvRecords.parse(new byte[0]);
        CsvRecords bomOnly = CsvRecords.parse(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        CsvRecords headerOnly = parse("a,b\n\n");

        assertThat(empty).isEqualTo(new CsvRecords(List.of(), List.of(), List.of(), false, 0));
        assertThat(bomOnly).isEqualTo(new CsvRecords(List.of(), List.of(), List.of(), false, 0));
        assertThat(headerOnly).isEqualTo(new CsvRecords(List.of("a", "b"), List.of(), List.of(), false, 0));
    }

    @Test
    void 行10_データ行のないヘッダーの閉じ忘れはヘッダーを読めず件数0() {
        CsvRecords records = parse("a,\"b\n");

        assertThat(records.headerUnreadable()).isTrue();
        assertThat(records.errors()).containsExactly(untilEndOfFile(1, 1, FormatDefect.UNCLOSED_QUOTE, "a,\"b"));
        assertThat(records.dataLineCount()).isZero();
    }

    @Test
    void 行11_BOM付きのファイルは行の原文にBOMと最後の行末の改行を含めない() {
        CsvRecords records = parse(BOM + "a,b\r\n1,2\r\n3,4\r\n");

        assertThat(records.header()).containsExactly("a", "b");
        assertThat(records.rows()).containsExactly(row(2, "1,2", "1", "2"), row(3, "3,4", "3", "4"));
    }

    @Test
    void 行12_Shift_JISの日本語を含むファイルは文字コードの誤りにし件数は最初のLFの後を空行を除いてLFで数える() {
        byte[] bytes =
                "external_id,external_version,acquired_at\n航海,1,2026-11-03T00:00:00Z\n\nV-2,1,2026-11-03T00:00:00Z\n"
                        .getBytes(Charset.forName("Shift_JIS"));

        CsvRecords records = CsvRecords.parse(bytes);

        assertThat(records.undecodable()).isTrue();
        assertThat(records.header()).isEmpty();
        assertThat(records.rows()).isEmpty();
        assertThat(records.errors()).isEmpty();
        assertThat(records.headerUnreadable()).isFalse();
        assertThat(records.dataLineCount()).isEqualTo(2);
    }

    @Test
    void 行12_UTF16のBOMのファイルは文字コードの誤りにする() {
        assertThat(CsvRecords.parse("a,b\n1,2\n".getBytes(StandardCharsets.UTF_16))
                        .undecodable())
                .isTrue();
    }

    @Test
    void 行12_途中で切れたUTF8のファイルは文字コードの誤りにする() {
        byte[] whole = "a\nあ".getBytes(StandardCharsets.UTF_8);

        CsvRecords records = CsvRecords.parse(Arrays.copyOf(whole, whole.length - 1));

        assertThat(records.undecodable()).isTrue();
        assertThat(records.dataLineCount()).isEqualTo(1);
    }

    @Test
    void 行15_受け取った件数は引用符の中の改行を含みレコードの外の空行を除いた物理行で行と誤りの範囲の和に一致する() {
        CsvRecords records = parse("a,b\n1,\"x\ny\"\n\n\n3,4\n\"5,6\n7,8\n");

        assertThat(records.rows()).containsExactly(row(2, 3, "1,\"x\ny\"", "1", "x\ny"), row(6, "3,4", "3", "4"));
        assertThat(records.errors()).containsExactly(untilEndOfFile(7, 8, FormatDefect.UNCLOSED_QUOTE, "\"5,6\n7,8"));
        assertThat(records.dataLineCount()).isEqualTo(5).isEqualTo(spannedLines(records));
    }
}
