package com.example.cargotracker.externaldata.interfaces.file;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 取込の組み立て（Bolt 39b2 の行 1〜12（5b・6b を含む）・15b・17・22）。解析とヘッダーの検証の結果から、形式の正しい行・行の誤り・取り込めなかった部分を
 * 持つ取込のコマンドを作る。キーの列（外部 ID・外部版・取得時刻）だけを検証する（値の列は 39b2b）。ファイルの誤りのときは行を読まない（変異 V10）。
 */
class IngestFileCommandAssemblerTest {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000039");
    private static final String MILESTONE_HEADER = "external_id,external_version,acquired_at,tracking_number";
    private static final String ACQUIRED = "2026-11-03T08:30:00Z";
    private static final Instant ACQUIRED_AT = Instant.parse(ACQUIRED);
    private static final List<String> MILESTONE_COLUMNS =
            List.of("external_id", "external_version", "acquired_at", "tracking_number");

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static IngestFileCommand milestones(String text) {
        return IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.MILESTONE, utf8(text));
    }

    private static String line(String externalId, String trackingNumber) {
        return externalId + ",1," + ACQUIRED + "," + trackingNumber;
    }

    private static IngestedRow ingested(int lineNumber, String externalId, String raw) {
        return ingested(lineNumber, lineNumber, externalId, raw);
    }

    private static IngestedRow ingested(int lineNumber, int lastLineNumber, String externalId, String raw) {
        return new IngestedRow(lineNumber, lastLineNumber, externalId, "1", ACQUIRED_AT, utf8(raw));
    }

    private static RejectedRow rejected(int lineNumber, int lastLineNumber, FormatDefect defect, String raw) {
        return rejected(lineNumber, lastLineNumber, defect, List.of(), raw);
    }

    private static RejectedRow rejected(
            int lineNumber, int lastLineNumber, FormatDefect defect, List<String> details, String raw) {
        return new RejectedRow(lineNumber, lastLineNumber, defect, details, utf8(raw));
    }

    private static UnreadablePart fileDefect(
            Integer fromLine, int lineCount, FormatDefect defect, List<String> details) {
        return new UnreadablePart(fromLine, lineCount, defect, details, null);
    }

    /** ファイルの誤りは行を読まない（形式の正しい行も行の誤りも持たない。変異 V10）。 */
    private static void assertNoRows(IngestFileCommand command) {
        assertThat(command.rows()).isEmpty();
        assertThat(command.rejectedRows()).isEmpty();
    }

    @Test
    void 形式の正しい行はキーの列の値と物理行の範囲と原文を持つ取り込む行にしファイル全体の原本と受け取った件数を持たせる() {
        String text = MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n"
                + line("MS-0002", "CTABCDEFGH2346") + "\n";

        IngestFileCommand command = milestones(text);

        assertThat(command.dataSourceId()).isEqualTo(SOURCE);
        assertThat(command.filePayload()).isEqualTo(utf8(text));
        assertThat(command.receivedCount()).isEqualTo(2);
        assertThat(command.rows())
                .containsExactly(
                        ingested(2, "MS-0001", line("MS-0001", "CTABCDEFGH2345")),
                        ingested(3, "MS-0002", line("MS-0002", "CTABCDEFGH2346")));
        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.unreadable()).isNull();
    }

    @Test
    void 行1_ヘッダーが読めないファイルは行を読まず最初のデータ行から受け取った件数のすべてを取り込めない部分にする() {
        String text = "external_id,\"external\"_version,acquired_at,tracking_number\n\n"
                + line("MS-0001", "CTABCDEFGH2345") + "\n" + line("MS-0002", "CTABCDEFGH2346") + "\n";

        IngestFileCommand command = milestones(text);

        assertNoRows(command);
        assertThat(command.receivedCount()).isEqualTo(2);
        assertThat(command.unreadable()).isEqualTo(fileDefect(3, 2, FormatDefect.UNREADABLE_HEADER, List.of()));
        assertThat(command.filePayload()).isEqualTo(utf8(text));
    }

    @Test
    void 行1_ヘッダーの中の引用符で囲まない値の中の引用符もヘッダーが読めずデータ行がなければ始まりはNULLで数は0() {
        IngestFileCommand command = milestones("external_id,external\"_version,acquired_at,tracking_number\n");

        assertNoRows(command);
        assertThat(command.receivedCount()).isZero();
        assertThat(command.unreadable()).isEqualTo(fileDefect(null, 0, FormatDefect.UNREADABLE_HEADER, List.of()));
    }

    @Test
    void 行2_必要な列がないファイルは行を読まず足りない列の名前を持つファイルの誤りにする() {
        IngestFileCommand command = IngestFileCommandAssembler.assemble(
                SOURCE, CsvFileKind.VOYAGE, utf8("external_id,external_version\nV-1,1\nV-2,1,extra\n"));

        assertNoRows(command);
        assertThat(command.receivedCount()).isEqualTo(2);
        assertThat(command.unreadable())
                .isEqualTo(fileDefect(2, 2, FormatDefect.MISSING_COLUMN, List.of("acquired_at")));
    }

    @Test
    void 行2_同じ名前の列があるファイルは行を読まず重なった列の名前を持つファイルの誤りにする() {
        IngestFileCommand command =
                milestones("external_id,external_id,external_version,acquired_at,tracking_number\nMS-0001,MS-0001,1,"
                        + ACQUIRED + ",CTABCDEFGH2345\n");

        assertNoRows(command);
        assertThat(command.unreadable())
                .isEqualTo(fileDefect(2, 1, FormatDefect.DUPLICATE_COLUMN, List.of("external_id")));
    }

    @Test
    void 行3_主要実績に追跡番号の列がないファイルは必要な列がないファイルの誤りにする() {
        IngestFileCommand command =
                milestones("external_id,external_version,acquired_at\nMS-0001,1," + ACQUIRED + "\n");

        assertNoRows(command);
        assertThat(command.unreadable())
                .isEqualTo(fileDefect(2, 1, FormatDefect.MISSING_COLUMN, List.of("tracking_number")));
    }

    @Test
    void 行4_列数の違う行はその行だけ列数を詳細に持つ行の誤りにしほかの行は取り込む行にする() {
        String tooMany = line("MS-0002", "CTABCDEFGH2346") + ",extra";
        String tooFew = "MS-0003,1," + ACQUIRED;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n"
                + tooMany + "\n" + tooFew + "\n" + line("MS-0004", "CTABCDEFGH2348") + "\n");

        assertThat(command.rows())
                .containsExactly(
                        ingested(2, "MS-0001", line("MS-0001", "CTABCDEFGH2345")),
                        ingested(5, "MS-0004", line("MS-0004", "CTABCDEFGH2348")));
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(3, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 4 列", "この行 5 列"), tooMany),
                        rejected(4, 4, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 4 列", "この行 3 列"), tooFew));
        assertThat(command.receivedCount()).isEqualTo(4);
        assertThat(command.unreadable()).isNull();
    }

    @Test
    void 行4_列数の違う行の列数は引用符の中のカンマと改行を値の中身として数える() {
        String quoted = "MS-0002,1," + ACQUIRED + ",\"CT,A\nB\",extra";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + quoted + "\n");

        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(2, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 4 列", "この行 5 列"), quoted));
    }

    @Test
    void 行5_引用符の閉じ忘れは前の行を取り込み閉じ忘れの行からファイルの終わりまでを原文付きの取り込めなかった部分にする() {
        String unclosed = "MS-0002,\"1," + ACQUIRED + ",CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347") + "\n"
                + line("MS-0004", "CTABCDEFGH2348");
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n" + unclosed + "\n");

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", line("MS-0001", "CTABCDEFGH2345")));
        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.unreadable())
                .isEqualTo(new UnreadablePart(3, 3, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));
        assertThat(command.receivedCount()).isEqualTo(4);
    }

    @Test
    void 行5b_閉じ忘れが最初のデータ行なら全データ行を取り込めない部分にする() {
        String unclosed = "MS-0001,\"1," + ACQUIRED + ",CTABCDEFGH2345\n" + line("MS-0002", "CTABCDEFGH2346");
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + unclosed + "\n");

        assertThat(command.rows()).isEmpty();
        assertThat(command.unreadable())
                .isEqualTo(new UnreadablePart(2, 2, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));
        assertThat(command.receivedCount()).isEqualTo(2);
    }

    @Test
    void 行5b_閉じ忘れが改行のない最後の行なら1行を取り込めない部分にする() {
        String unclosed = "MS-0002,1," + ACQUIRED + ",\"CTABCDEFGH2346";
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n" + unclosed);

        assertThat(command.rows()).hasSize(1);
        assertThat(command.unreadable())
                .isEqualTo(new UnreadablePart(3, 1, FormatDefect.UNCLOSED_QUOTE, List.of(), utf8(unclosed)));
    }

    @Test
    void 行5b_ヘッダーの閉じ忘れはヘッダーが読めないファイルの誤りにし最初のデータ行から受け取った件数のすべてを取り込めない() {
        IngestFileCommand command = milestones("external_id,\"external_version,acquired_at,tracking_number\n"
                + line("MS-0001", "CTABCDEFGH2345") + "\n" + line("MS-0002", "CTABCDEFGH2346") + "\n");

        assertNoRows(command);
        assertThat(command.receivedCount()).isEqualTo(2);
        assertThat(command.unreadable()).isEqualTo(fileDefect(2, 2, FormatDefect.UNREADABLE_HEADER, List.of()));
    }

    @Test
    void 行15b_ヘッダーで飲み込んだ範囲の空行も受け取った件数と取り込めなかった物理行に数える() {
        IngestFileCommand command = milestones("external_id,\"external_version,acquired_at,tracking_number\n"
                + line("MS-0001", "CTABCDEFGH2345") + "\n\n" + line("MS-0002", "CTABCDEFGH2346") + "\n");

        assertNoRows(command);
        assertThat(command.receivedCount()).isEqualTo(3);
        assertThat(command.unreadable()).isEqualTo(fileDefect(2, 3, FormatDefect.UNREADABLE_HEADER, List.of()));
    }

    @Test
    void 行6_閉じた引用符の後の文字の行は引用符の対応を追った終わりまでを行の誤りにし空行と改行を含む値の後の行も読む() {
        String broken = "MS-0001,\"1\"x," + ACQUIRED + ",\"CT\nAB\"";
        String multiLine = "MS-0002,1," + ACQUIRED + ",\"CTABCDEFGH2346\nNOTE\"";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + broken + "\n\n" + multiLine + "\n");

        assertThat(command.rejectedRows())
                .containsExactly(rejected(2, 3, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, broken));
        assertThat(command.rows()).containsExactly(ingested(5, 6, "MS-0002", multiLine));
        assertThat(command.receivedCount()).isEqualTo(4);
        assertThat(command.unreadable()).isNull();
    }

    @Test
    void 行6b_読み飛ばす途中で引用符が閉じないままファイルの終わりまで飲み込めば前の行を取り込みその行からを取り込めない部分にする() {
        String swallowed = "MS-0002,\"1\"x," + ACQUIRED + ",\"CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347");
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n" + swallowed + "\n");

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", line("MS-0001", "CTABCDEFGH2345")));
        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.unreadable())
                .isEqualTo(new UnreadablePart(3, 2, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, List.of(), utf8(swallowed)));
        assertThat(command.receivedCount()).isEqualTo(3);
    }

    @Test
    void 行7_引用符で囲まない値の中の引用符の行はその行だけ行の誤りにする() {
        String bare = "MS-0002,1\"x," + ACQUIRED + ",CTABCDEFGH2346";
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + bare + "\n" + line("MS-0003", "CTABCDEFGH2347"));

        assertThat(command.rejectedRows()).containsExactly(rejected(2, 2, FormatDefect.BARE_QUOTE, bare));
        assertThat(command.rows()).containsExactly(ingested(3, "MS-0003", line("MS-0003", "CTABCDEFGH2347")));
    }

    @Test
    void 行8_取得時刻にオフセットがないか日付として誤りなら列名を持つ時刻の書式の行の誤りにする() {
        String noOffset = "MS-0001,1,2026-11-03T08:30:00,CTABCDEFGH2345";
        String noSuchDate = "MS-0002,1,2026-02-30T08:30:00Z,CTABCDEFGH2346";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + noOffset + "\n" + noSuchDate + "\n");

        assertThat(command.rows()).isEmpty();
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(2, 2, FormatDefect.INVALID_TIME, List.of("acquired_at"), noOffset),
                        rejected(3, 3, FormatDefect.INVALID_TIME, List.of("acquired_at"), noSuchDate));
    }

    @Test
    void 行8_オフセット付きの取得時刻は秒と小数を省いてよくマイクロ秒より細かい桁を切り捨てる() {
        String withoutSeconds = "MS-0001,1,2026-11-03T17:30+09:00,CTABCDEFGH2345";
        String nanos = "MS-0002,1,2026-11-03T08:30:00.123456789Z,CTABCDEFGH2346";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + withoutSeconds + "\n" + nanos + "\n");

        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.rows())
                .extracting(IngestedRow::acquiredAt)
                .containsExactly(ACQUIRED_AT, Instant.parse("2026-11-03T08:30:00.123456Z"));
    }

    @Test
    void 行9_キーの列が空か空白だけなら列名を持つ空の値の行の誤りにする() {
        String emptyId = ",1," + ACQUIRED + ",CTABCDEFGH2345";
        String blankVersion = "MS-0002,  ," + ACQUIRED + ",CTABCDEFGH2346";
        String emptyAcquired = "MS-0003,1,,CTABCDEFGH2347";
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + emptyId + "\n" + blankVersion + "\n" + emptyAcquired + "\n");

        assertThat(command.rows()).isEmpty();
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(2, 2, FormatDefect.EMPTY_VALUE, List.of("external_id"), emptyId),
                        rejected(3, 3, FormatDefect.EMPTY_VALUE, List.of("external_version"), blankVersion),
                        rejected(4, 4, FormatDefect.EMPTY_VALUE, List.of("acquired_at"), emptyAcquired));
    }

    @Test
    void 行9_空でないキーの値は前後の空白を外さずに渡す() {
        String padded = " MS-0001 , 1 ," + ACQUIRED + ",CTABCDEFGH2345";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + padded + "\n");

        assertThat(command.rows())
                .containsExactly(new IngestedRow(2, 2, " MS-0001 ", " 1 ", ACQUIRED_AT, utf8(padded)));
    }

    @Test
    void 行10_0バイトとBOMだけのファイルは必要な列がないファイルの誤りで件数0と始まりNULLにする() {
        UnreadablePart expected = fileDefect(null, 0, FormatDefect.MISSING_COLUMN, MILESTONE_COLUMNS);

        IngestFileCommand empty = IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.MILESTONE, new byte[0]);
        IngestFileCommand bomOnly = milestones(BOM);

        assertNoRows(empty);
        assertThat(empty.receivedCount()).isZero();
        assertThat(empty.unreadable()).isEqualTo(expected);
        assertNoRows(bomOnly);
        assertThat(bomOnly.receivedCount()).isZero();
        assertThat(bomOnly.unreadable()).isEqualTo(expected);
    }

    @Test
    void 行10_ヘッダーだけで列がそろえば件数0で取り込めなかった部分はなく列が足りなければ必要な列がないファイルの誤りにする() {
        IngestFileCommand complete = milestones(MILESTONE_HEADER + "\n");
        IngestFileCommand lacking = milestones("external_id,external_version,acquired_at\n");

        assertNoRows(complete);
        assertThat(complete.receivedCount()).isZero();
        assertThat(complete.unreadable()).isNull();
        assertNoRows(lacking);
        assertThat(lacking.receivedCount()).isZero();
        assertThat(lacking.unreadable())
                .isEqualTo(fileDefect(null, 0, FormatDefect.MISSING_COLUMN, List.of("tracking_number")));
    }

    @Test
    void 行10_ヘッダーだけで閉じ忘れたファイルはヘッダーが読めないファイルの誤りで件数0と始まりNULLにする() {
        IngestFileCommand command = milestones("external_id,\"external_version,acquired_at,tracking_number\n");

        assertNoRows(command);
        assertThat(command.receivedCount()).isZero();
        assertThat(command.unreadable()).isEqualTo(fileDefect(null, 0, FormatDefect.UNREADABLE_HEADER, List.of()));
    }

    @Test
    void 行11_BOM付きのファイルは行の原文にBOMを含めずファイル全体の原本はBOMを含むまま持たせる() {
        String text = BOM + MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n";

        IngestFileCommand command = milestones(text);

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", line("MS-0001", "CTABCDEFGH2345")));
        assertThat(command.filePayload()).isEqualTo(utf8(text));
    }

    @Test
    void 行12_Shift_JISのファイルは行を読まず文字コードの誤りにし最初のデータ行から受け取った件数のすべてを取り込めない() {
        byte[] file = (MILESTONE_HEADER + "\n\n" + line("航海一", "CTABCDEFGH2345") + "\n" + line("航海二", "CTABCDEFGH2346")
                        + "\n")
                .getBytes(Charset.forName("Shift_JIS"));

        IngestFileCommand command = IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.MILESTONE, file);

        assertNoRows(command);
        assertThat(command.receivedCount()).isEqualTo(2);
        assertThat(command.unreadable()).isEqualTo(fileDefect(3, 2, FormatDefect.UNDECODABLE, List.of()));
        assertThat(command.filePayload()).isEqualTo(file);
    }

    @Test
    void 行12_UTF_16のBOMのファイルは文字コードの誤りにする() {
        byte[] file =
                (MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n").getBytes(StandardCharsets.UTF_16);

        IngestFileCommand command = IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.MILESTONE, file);

        assertNoRows(command);
        assertThat(command.unreadable()).isEqualTo(fileDefect(2, 1, FormatDefect.UNDECODABLE, List.of()));
    }

    @Test
    void 行17_外部IDは200文字外部版は100文字まで取り込み超えれば列名と上限を持つ長すぎる値の行の誤りにする() {
        String longestId = "I".repeat(200);
        String longestVersion = "9".repeat(100);
        String fits = longestId + "," + longestVersion + "," + ACQUIRED + ",CTABCDEFGH2345";
        String idTooLong = "I".repeat(201) + ",1," + ACQUIRED + ",CTABCDEFGH2346";
        String versionTooLong = "MS-0003," + "9".repeat(101) + "," + ACQUIRED + ",CTABCDEFGH2347";
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + fits + "\n" + idTooLong + "\n" + versionTooLong + "\n");

        assertThat(command.rows())
                .containsExactly(new IngestedRow(2, 2, longestId, longestVersion, ACQUIRED_AT, utf8(fits)));
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(3, 3, FormatDefect.TOO_LONG, List.of("external_id", "200 文字まで"), idTooLong),
                        rejected(4, 4, FormatDefect.TOO_LONG, List.of("external_version", "100 文字まで"), versionTooLong));
    }

    /**
     * 行 22 の層の規則を外部データに絞って確かめる（全体の規則は LayerArchitectureTest・ModularityTest、形式の誤りと物理行の範囲が用語集に
     * あることは LivingGlossaryConsistencyTest）。application は解析と組み立ての型を参照せず、組み立てはリポジトリを使わない。
     */
    @Test
    void 行22_applicationはinterfaces_fileを参照せず組み立てはリポジトリを使わない() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.cargotracker.externaldata");

        noClasses()
                .that()
                .resideInAPackage("..externaldata.application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..externaldata.interfaces.file..")
                .check(classes);
        noClasses()
                .that()
                .resideInAPackage("..externaldata.interfaces.file..")
                .should()
                .dependOnClassesThat()
                .haveSimpleNameEndingWith("Repository")
                .check(classes);
        assertThat(classes.contain(IngestFileCommandAssembler.class)).isTrue();
    }

    @Test
    void 行17_長さは文字の数で数え補助文字も1文字にする() {
        String surrogate = new String(Character.toChars(0x20BB7));
        String longestId = surrogate.repeat(200);
        String fits = longestId + ",1," + ACQUIRED + ",CTABCDEFGH2345";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + fits + "\n");

        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.rows()).extracting(IngestedRow::externalId).containsExactly(longestId);
    }
}
