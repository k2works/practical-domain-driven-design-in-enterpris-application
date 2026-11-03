package com.example.cargotracker.externaldata.interfaces.file;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.IngestedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.RejectedRow;
import com.example.cargotracker.externaldata.application.internal.commands.IngestFileCommand.UnreadablePart;
import com.example.cargotracker.externaldata.domain.model.valueobjects.FormatDefect;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneKindValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.MilestoneValues;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.TrackingNumberValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 取込の組み立て（Bolt 39b2 の行 1〜12（5b・6b を含む）・15b・17・22。値の列は入れ子のクラスで Bolt 39b2b の行番号）。解析とヘッダーの検証の
 * 結果から、形式の正しい行・行の誤り・取り込めなかった部分を持つ取込のコマンドを作る。キーの列（外部 ID・外部版・取得時刻）を検証し、次に値の列から
 * 取込の値を作る。ファイルの誤りのときは行を読まない（変異 V10）。主要実績の行は追跡番号の形と 6 種類、航海の行は寄港 2 つ以上（T-230）。
 */
class IngestFileCommandAssemblerTest {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000039");
    private static final String MILESTONE_HEADER =
            "external_id,external_version,acquired_at,tracking_number,kind,location_unlocode,occurred_at";
    private static final String VOYAGE_HEADER = "external_id,external_version,acquired_at,port_calls";
    private static final String ACQUIRED = "2026-11-03T08:30:00Z";
    private static final Instant ACQUIRED_AT = Instant.parse(ACQUIRED);
    private static final String OCCURRED = "2026-11-03T08:00:00Z";
    private static final Location TOKYO = new Location("JPTYO");

    /** 主要実績の行の値の列（追跡番号の後。出発・東京・取得時刻より前の発生時刻）。 */
    private static final String MILESTONE_TAIL = ",DEPARTURE,JPTYO," + OCCURRED;

    /** 主要実績のキーの列の後の列（追跡番号と値の列）。 */
    private static final List<String> MILESTONE_VALUE_COLUMNS =
            List.of("tracking_number", "kind", "location_unlocode", "occurred_at");

    private static final List<String> MILESTONE_COLUMNS = List.of(
            "external_id",
            "external_version",
            "acquired_at",
            "tracking_number",
            "kind",
            "location_unlocode",
            "occurred_at");

    /** UTF-8 の BOM（U+FEFF）。見えない文字をソースに書かないよう数で書く。 */
    private static final String BOM = String.valueOf((char) 0xFEFF);

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static IngestFileCommand milestones(String text) {
        return IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.MILESTONE, utf8(text));
    }

    private static IngestFileCommand voyages(String text) {
        return IngestFileCommandAssembler.assemble(SOURCE, CsvFileKind.VOYAGE, utf8(text));
    }

    private static String line(String externalId, String trackingNumber) {
        return externalId + ",1," + ACQUIRED + "," + trackingNumber + MILESTONE_TAIL;
    }

    /** 値の既定（出発・東京・発生時刻）の主要実績の値。 */
    private static MilestoneValues milestoneValues(String trackingNumber) {
        return new MilestoneValues(
                new TrackingNumberValue(trackingNumber),
                MilestoneKindValue.DEPARTURE,
                TOKYO,
                new UtcInstant(Instant.parse(OCCURRED)));
    }

    private static IngestedRow ingested(int lineNumber, String externalId, String trackingNumber) {
        return ingested(lineNumber, lineNumber, externalId, trackingNumber, line(externalId, trackingNumber));
    }

    private static IngestedRow ingested(
            int lineNumber, int lastLineNumber, String externalId, String trackingNumber, String raw) {
        return new IngestedRow(
                lineNumber, lastLineNumber, externalId, "1", ACQUIRED_AT, milestoneValues(trackingNumber), utf8(raw));
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
                .containsExactly(ingested(2, "MS-0001", "CTABCDEFGH2345"), ingested(3, "MS-0002", "CTABCDEFGH2346"));
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
                .isEqualTo(fileDefect(2, 2, FormatDefect.MISSING_COLUMN, List.of("acquired_at", "port_calls")));
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
                .isEqualTo(fileDefect(2, 1, FormatDefect.MISSING_COLUMN, MILESTONE_VALUE_COLUMNS));
    }

    @Test
    void 行4_列数の違う行はその行だけ列数を詳細に持つ行の誤りにしほかの行は取り込む行にする() {
        String tooMany = line("MS-0002", "CTABCDEFGH2346") + ",extra";
        String tooFew = "MS-0003,1," + ACQUIRED;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n"
                + tooMany + "\n" + tooFew + "\n" + line("MS-0004", "CTABCDEFGH2348") + "\n");

        assertThat(command.rows())
                .containsExactly(ingested(2, "MS-0001", "CTABCDEFGH2345"), ingested(5, "MS-0004", "CTABCDEFGH2348"));
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(3, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 7 列", "この行 8 列"), tooMany),
                        rejected(4, 4, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 7 列", "この行 3 列"), tooFew));
        assertThat(command.receivedCount()).isEqualTo(4);
        assertThat(command.unreadable()).isNull();
    }

    @Test
    void 行4_列数の違う行の列数は引用符の中のカンマと改行を値の中身として数える() {
        String quoted = "MS-0002,1," + ACQUIRED + ",\"CT,A\nB\"" + MILESTONE_TAIL + ",extra";
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + quoted + "\n");

        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(2, 3, FormatDefect.COLUMN_COUNT_MISMATCH, List.of("ヘッダー 7 列", "この行 8 列"), quoted));
    }

    @Test
    void 行5_引用符の閉じ忘れは前の行を取り込み閉じ忘れの行からファイルの終わりまでを原文付きの取り込めなかった部分にする() {
        String unclosed = "MS-0002,\"1," + ACQUIRED + ",CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347") + "\n"
                + line("MS-0004", "CTABCDEFGH2348");
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n" + unclosed + "\n");

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", "CTABCDEFGH2345"));
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
        String multiLine = line("MS-0002", "CTABCDEFGH2346") + ",\"港で荷役待ち\nNOTE\"";
        IngestFileCommand command = milestones(MILESTONE_HEADER + ",remarks\n" + broken + "\n\n" + multiLine + "\n");

        assertThat(command.rejectedRows())
                .containsExactly(rejected(2, 3, FormatDefect.TEXT_AFTER_CLOSING_QUOTE, broken));
        assertThat(command.rows()).containsExactly(ingested(5, 6, "MS-0002", "CTABCDEFGH2346", multiLine));
        assertThat(command.receivedCount()).isEqualTo(4);
        assertThat(command.unreadable()).isNull();
    }

    @Test
    void 行6b_読み飛ばす途中で引用符が閉じないままファイルの終わりまで飲み込めば前の行を取り込みその行からを取り込めない部分にする() {
        String swallowed = "MS-0002,\"1\"x," + ACQUIRED + ",\"CTABCDEFGH2346\n" + line("MS-0003", "CTABCDEFGH2347");
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + line("MS-0001", "CTABCDEFGH2345") + "\n" + swallowed + "\n");

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", "CTABCDEFGH2345"));
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
        assertThat(command.rows()).containsExactly(ingested(3, "MS-0003", "CTABCDEFGH2347"));
    }

    @Test
    void 行8_取得時刻にオフセットがないか日付として誤りなら列名を持つ時刻の書式の行の誤りにする() {
        String noOffset = "MS-0001,1,2026-11-03T08:30:00,CTABCDEFGH2345" + MILESTONE_TAIL;
        String noSuchDate = "MS-0002,1,2026-02-30T08:30:00Z,CTABCDEFGH2346" + MILESTONE_TAIL;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + noOffset + "\n" + noSuchDate + "\n");

        assertThat(command.rows()).isEmpty();
        assertThat(command.rejectedRows())
                .containsExactly(
                        rejected(2, 2, FormatDefect.INVALID_TIME, List.of("acquired_at"), noOffset),
                        rejected(3, 3, FormatDefect.INVALID_TIME, List.of("acquired_at"), noSuchDate));
    }

    @Test
    void 行8_オフセット付きの取得時刻は秒と小数を省いてよくマイクロ秒より細かい桁を切り捨てる() {
        String withoutSeconds = "MS-0001,1,2026-11-03T17:30+09:00,CTABCDEFGH2345" + MILESTONE_TAIL;
        String nanos = "MS-0002,1,2026-11-03T08:30:00.123456789Z,CTABCDEFGH2346" + MILESTONE_TAIL;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + withoutSeconds + "\n" + nanos + "\n");

        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.rows())
                .extracting(IngestedRow::acquiredAt)
                .containsExactly(ACQUIRED_AT, Instant.parse("2026-11-03T08:30:00.123456Z"));
    }

    @Test
    void 行9_キーの列が空か空白だけなら列名を持つ空の値の行の誤りにする() {
        String emptyId = ",1," + ACQUIRED + ",CTABCDEFGH2345" + MILESTONE_TAIL;
        String blankVersion = "MS-0002,  ," + ACQUIRED + ",CTABCDEFGH2346" + MILESTONE_TAIL;
        String emptyAcquired = "MS-0003,1,,CTABCDEFGH2347" + MILESTONE_TAIL;
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
        String padded = " MS-0001 , 1 ," + ACQUIRED + ",CTABCDEFGH2345" + MILESTONE_TAIL;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + padded + "\n");

        assertThat(command.rows())
                .containsExactly(new IngestedRow(
                        2, 2, " MS-0001 ", " 1 ", ACQUIRED_AT, milestoneValues("CTABCDEFGH2345"), utf8(padded)));
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
                .isEqualTo(fileDefect(null, 0, FormatDefect.MISSING_COLUMN, MILESTONE_VALUE_COLUMNS));
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

        assertThat(command.rows()).containsExactly(ingested(2, "MS-0001", "CTABCDEFGH2345"));
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
        String fits = longestId + "," + longestVersion + "," + ACQUIRED + ",CTABCDEFGH2345" + MILESTONE_TAIL;
        String idTooLong = "I".repeat(201) + ",1," + ACQUIRED + ",CTABCDEFGH2346" + MILESTONE_TAIL;
        String versionTooLong = "MS-0003," + "9".repeat(101) + "," + ACQUIRED + ",CTABCDEFGH2347" + MILESTONE_TAIL;
        IngestFileCommand command =
                milestones(MILESTONE_HEADER + "\n" + fits + "\n" + idTooLong + "\n" + versionTooLong + "\n");

        assertThat(command.rows())
                .containsExactly(new IngestedRow(
                        2, 2, longestId, longestVersion, ACQUIRED_AT, milestoneValues("CTABCDEFGH2345"), utf8(fits)));
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
        String fits = longestId + ",1," + ACQUIRED + ",CTABCDEFGH2345" + MILESTONE_TAIL;
        IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + fits + "\n");

        assertThat(command.rejectedRows()).isEmpty();
        assertThat(command.rows()).extracting(IngestedRow::externalId).containsExactly(longestId);
    }

    /**
     * 値の列（Bolt 39b2b の行 1〜17・25）。キーの列の後に値の列を必要な列の順に確かめ、最初の 1 つの誤りを行の誤りにする。値の誤りの行は
     * 取り込む行にせず、値は加工しない。正しい行は取込の値（航海の値か主要実績の値）を持つ。
     */
    @Nested
    class 値の列 {

        /** 寄港 2 つ（始港の到着が空・終港の出発が空。オフセット付きの時刻）。 */
        private static final String TWO_CALLS = "JPTYO;;2026-11-20T08:00:00+09:00|USLAX;2026-12-01T06:00:00-08:00;";

        private static final String TRACKING_NUMBER = "CTABCDEFGH2345";

        private static String voyageLine(String externalId, String portCalls) {
            return externalId + ",1," + ACQUIRED + "," + portCalls;
        }

        private static String milestoneLine(String trackingNumber, String kind, String location, String occurred) {
            return "MS-0001,1," + ACQUIRED + "," + trackingNumber + "," + kind + "," + location + "," + occurred;
        }

        private static UtcInstant utc(String instant) {
            return instant == null ? null : new UtcInstant(Instant.parse(instant));
        }

        private static PortCallValue call(String port, String arrival, String departure) {
            return new PortCallValue(new Location(port), utc(arrival), utc(departure));
        }

        private static void assertVoyageRejected(String portCalls, FormatDefect defect, String... details) {
            String raw = voyageLine("V2611E", portCalls);
            IngestFileCommand command = voyages(VOYAGE_HEADER + "\n" + raw + "\n");

            assertThat(command.rows()).as("寄港の列 %s", portCalls).isEmpty();
            assertThat(command.rejectedRows())
                    .as("寄港の列 %s", portCalls)
                    .containsExactly(rejected(2, 2, defect, List.of(details), raw));
        }

        private static void assertMilestoneRejected(String raw, FormatDefect defect, String... details) {
            IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + raw + "\n");

            assertThat(command.rows()).as("行 %s", raw).isEmpty();
            assertThat(command.rejectedRows())
                    .as("行 %s", raw)
                    .containsExactly(rejected(2, 2, defect, List.of(details), raw));
        }

        @Test
        void 行1_航海の正しい行は寄港の並びを持つ航海の値にし時刻をUTCに直してマイクロ秒に切り捨てる() {
            String two = voyageLine("V2611E", TWO_CALLS);
            String three = voyageLine(
                    "V2612W",
                    "JPTYO;;2026-11-20T00:00:00Z|SGSIN;2026-11-25T00:00:00Z;2026-11-25T00:00:00Z"
                            + "|NLRTM;2026-12-10T00:00:00.123456789Z;");

            IngestFileCommand command = voyages(VOYAGE_HEADER + "\n" + two + "\n" + three + "\n");

            assertThat(command.rejectedRows()).isEmpty();
            assertThat(command.rows())
                    .extracting(IngestedRow::values)
                    .containsExactly(
                            new VoyageValues(
                                    "V2611E",
                                    List.of(
                                            call("JPTYO", null, "2026-11-19T23:00:00Z"),
                                            call("USLAX", "2026-12-01T14:00:00Z", null))),
                            new VoyageValues(
                                    "V2612W",
                                    List.of(
                                            call("JPTYO", null, "2026-11-20T00:00:00Z"),
                                            call("SGSIN", "2026-11-25T00:00:00Z", "2026-11-25T00:00:00Z"),
                                            call("NLRTM", "2026-12-10T00:00:00.123456Z", null))));
        }

        @Test
        void 行2_寄港が1つの航海の行は寄港の列の形の行の誤りにする() {
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z", FormatDefect.INVALID_PORT_CALLS, "port_calls", "寄港が足りません（2 つ以上）");
        }

        @Test
        void 行3_寄港の区切りの誤りは寄港の番号を持つ寄港の列の形の行の誤りにする() {
            assertVoyageRejected(
                    "JPTYO;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "1 つ目の寄港");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z;|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "1 つ目の寄港");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z||USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "2 つ目の寄港");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;|",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "3 つ目の寄港");
        }

        @Test
        void 行4_寄港のUN_LOCODEの誤りは寄港の番号を持つ場所の形の行の誤りにする() {
            assertVoyageRejected(
                    "jptyo;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_LOCATION,
                    "port_calls",
                    "1 つ目の寄港");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z|USLA;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_LOCATION,
                    "port_calls",
                    "2 つ目の寄港");
            assertVoyageRejected(
                    "JPTY1;;2026-11-20T00:00:00Z|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_LOCATION,
                    "port_calls",
                    "1 つ目の寄港");
        }

        @Test
        void 行5_到着より前の出発と到着も出発もない寄港は寄港の列の形の行の誤りにする() {
            assertVoyageRejected(
                    "JPTYO;2026-11-20T10:00:00Z;2026-11-20T09:00:00Z|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "1 つ目の寄港",
                    "到着より前に出発しています");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z|USLAX;;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "2 つ目の寄港",
                    "到着か出発が要ります");
        }

        @Test
        void 行6_寄港の時刻の書式の誤りは寄港の番号と到着か出発を持つ時刻の書式の行の誤りにする() {
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T08:00:00|USLAX;2026-12-01T00:00:00Z;",
                    FormatDefect.INVALID_TIME,
                    "port_calls",
                    "1 つ目の寄港",
                    "出発");
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z|USLAX;2026-02-30T00:00:00Z;",
                    FormatDefect.INVALID_TIME,
                    "port_calls",
                    "2 つ目の寄港",
                    "到着");
        }

        @Test
        void 行7_同じ寄港が2つあれば行の誤りにし同じ港で時刻の違う寄港は受ける() {
            String visit = "|USLAX;2026-12-01T00:00:00Z;2026-12-02T00:00:00Z";
            assertVoyageRejected(
                    "JPTYO;;2026-11-20T00:00:00Z" + visit + visit + "|JPTYO;2026-12-15T00:00:00Z;",
                    FormatDefect.INVALID_PORT_CALLS,
                    "port_calls",
                    "同じ寄港があります");

            IngestFileCommand roundTrip = voyages(VOYAGE_HEADER + "\n"
                    + voyageLine("V2611E", "JPTYO;;2026-11-20T00:00:00Z" + visit + "|JPTYO;2026-12-15T00:00:00Z;")
                    + "\n");

            assertThat(roundTrip.rejectedRows()).isEmpty();
            assertThat(roundTrip.rows())
                    .extracting(IngestedRow::values)
                    .containsExactly(new VoyageValues(
                            "V2611E",
                            List.of(
                                    call("JPTYO", null, "2026-11-20T00:00:00Z"),
                                    call("USLAX", "2026-12-01T00:00:00Z", "2026-12-02T00:00:00Z"),
                                    call("JPTYO", "2026-12-15T00:00:00Z", null))));
        }

        @ParameterizedTest
        @EnumSource(MilestoneKindValue.class)
        void 行8_主要実績の正しい行は追跡番号と種類と場所と発生時刻を持つ主要実績の値にする(MilestoneKindValue kind) {
            String raw = milestoneLine(TRACKING_NUMBER, kind.name(), "JPTYO", "2026-11-03T17:00:00+09:00");

            IngestFileCommand command = milestones(MILESTONE_HEADER + "\n" + raw + "\n");

            assertThat(command.rejectedRows()).isEmpty();
            assertThat(command.rows())
                    .containsExactly(new IngestedRow(
                            2,
                            2,
                            "MS-0001",
                            "1",
                            ACQUIRED_AT,
                            new MilestoneValues(
                                    new TrackingNumberValue(TRACKING_NUMBER), kind, TOKYO, utc("2026-11-03T08:00:00Z")),
                            utf8(raw)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"LOADED", "pickup", " PICKUP"})
        void 行9_6つの種類のどれでもない種類は加工せずに種類の行の誤りにする(String kind) {
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, kind, "JPTYO", OCCURRED), FormatDefect.INVALID_KIND, "kind");
        }

        @ParameterizedTest
        @ValueSource(strings = {"jptyo", "JPTY", "JPTY1"})
        void 行10_UN_LOCODEの形でない場所は場所の形の行の誤りにする(String location) {
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "DEPARTURE", location, OCCURRED),
                    FormatDefect.INVALID_LOCATION,
                    "location_unlocode");
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "CTABCDEFGH2340",
                    "CTABCDEFGH234O",
                    "CTABCDEFGH2341",
                    "CTABCDEFGH234I",
                    "CTABCDEFGH234L",
                    "CTABCDEFGH234",
                    "CTABCDEFGH23456",
                    "ctABCDEFGH2345",
                    "CXABCDEFGH2345"
                })
        void 行11_追跡番号の形でない追跡番号は追跡番号の形の行の誤りにする(String trackingNumber) {
            assertMilestoneRejected(
                    milestoneLine(trackingNumber, "DEPARTURE", "JPTYO", OCCURRED),
                    FormatDefect.INVALID_TRACKING_NUMBER,
                    "tracking_number");
        }

        @Test
        void 行12_発生時刻の書式の誤りは時刻の書式の行の誤りにしナノ秒はマイクロ秒に切り捨てる() {
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "DEPARTURE", "JPTYO", "2026-11-03T08:00:00"),
                    FormatDefect.INVALID_TIME,
                    "occurred_at");
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "DEPARTURE", "JPTYO", "2026-02-30T08:00:00Z"),
                    FormatDefect.INVALID_TIME,
                    "occurred_at");

            IngestFileCommand nanos = milestones(MILESTONE_HEADER + "\n"
                    + milestoneLine(TRACKING_NUMBER, "DEPARTURE", "JPTYO", "2026-11-03T08:00:00.123456789Z") + "\n");

            assertThat(nanos.rows())
                    .extracting(row -> ((MilestoneValues) row.values()).occurredAt())
                    .containsExactly(utc("2026-11-03T08:00:00.123456Z"));
        }

        @Test
        void 行13_値の列が空か空白だけなら列名を持つ空の値の行の誤りにする() {
            assertMilestoneRejected(
                    milestoneLine("", "DEPARTURE", "JPTYO", OCCURRED), FormatDefect.EMPTY_VALUE, "tracking_number");
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "  ", "JPTYO", OCCURRED), FormatDefect.EMPTY_VALUE, "kind");
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "DEPARTURE", "", OCCURRED),
                    FormatDefect.EMPTY_VALUE,
                    "location_unlocode");
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "DEPARTURE", "JPTYO", ""), FormatDefect.EMPTY_VALUE, "occurred_at");
            assertVoyageRejected("", FormatDefect.EMPTY_VALUE, "port_calls");
            assertVoyageRejected("  ", FormatDefect.EMPTY_VALUE, "port_calls");
        }

        @Test
        void 行14_航海番号は30文字まで取り込み31文字なら外部IDの長すぎる値の行の誤りにし補助文字も1文字に数える() {
            String longest = "V".repeat(30);
            String surrogates = new String(Character.toChars(0x20BB7)).repeat(30);
            String tooLong = voyageLine("V".repeat(31), TWO_CALLS);

            IngestFileCommand command = voyages(VOYAGE_HEADER + "\n" + voyageLine(longest, TWO_CALLS) + "\n"
                    + voyageLine(surrogates, TWO_CALLS) + "\n" + tooLong + "\n");

            assertThat(command.rows())
                    .extracting(row -> ((VoyageValues) row.values()).voyageNumber())
                    .containsExactly(longest, surrogates);
            assertThat(command.rejectedRows())
                    .containsExactly(rejected(4, 4, FormatDefect.TOO_LONG, List.of("external_id", "30 文字まで"), tooLong));
        }

        @Test
        void 行15_値の列がヘッダーになければ行を読まず必要な列がないファイルの誤りにする() {
            IngestFileCommand voyage =
                    voyages("external_id,external_version,acquired_at\n" + "V2611E,1," + ACQUIRED + "\n");

            assertNoRows(voyage);
            assertThat(voyage.unreadable())
                    .isEqualTo(fileDefect(2, 1, FormatDefect.MISSING_COLUMN, List.of("port_calls")));
            for (String missing : List.of("kind", "location_unlocode", "occurred_at")) {
                String header = MILESTONE_HEADER.replace("," + missing, "");
                IngestFileCommand milestone = milestones(header + "\n" + line("MS-0001", TRACKING_NUMBER) + "\n");

                assertNoRows(milestone);
                assertThat(milestone.unreadable())
                        .as("%s のないヘッダー", missing)
                        .isEqualTo(fileDefect(2, 1, FormatDefect.MISSING_COLUMN, List.of(missing)));
            }
        }

        @Test
        void 行16_キーの列の誤りを値の列より先にし値の列は必要な列の順で最初の1つを行の誤りにする() {
            assertMilestoneRejected(
                    "MS-0001,1,2026-11-03T08:30:00,ctABCDEFGH2345,LOADED,JPTYO," + OCCURRED,
                    FormatDefect.INVALID_TIME,
                    "acquired_at");
            assertMilestoneRejected(
                    milestoneLine(TRACKING_NUMBER, "LOADED", "jptyo", OCCURRED), FormatDefect.INVALID_KIND, "kind");
            assertMilestoneRejected(
                    milestoneLine("CTABCDEFGH234O", "DEPARTURE", "JPTYO", "2026-11-03T08:00:00"),
                    FormatDefect.INVALID_TRACKING_NUMBER,
                    "tracking_number");

            String reordered = "2026-11-03T08:00:00,JPTYO,LOADED," + TRACKING_NUMBER + "," + ACQUIRED + ",1,MS-0001";
            IngestFileCommand command = milestones(
                    "occurred_at,location_unlocode,kind,tracking_number,acquired_at,external_version,external_id\n"
                            + reordered + "\n");

            assertThat(command.rejectedRows())
                    .containsExactly(rejected(2, 2, FormatDefect.INVALID_KIND, List.of("kind"), reordered));
        }

        @Test
        void 行17_正しい行と値の誤りの行が混ざれば値の誤りの行だけを行の誤りにし後の行も読む() {
            String first = line("MS-0001", "CTABCDEFGH2345") + ",";
            String wrong = "MS-0002,1," + ACQUIRED + ",CTABCDEFGH234O" + MILESTONE_TAIL + ",";
            String multiLine = line("MS-0003", "CTABCDEFGH2347") + ",\"港で荷役待ち\n翌日に積み込み\"";
            String last = line("MS-0004", "CTABCDEFGH2348") + ",";

            IngestFileCommand command = milestones(
                    MILESTONE_HEADER + ",remarks\n" + first + "\n" + wrong + "\n" + multiLine + "\n\n" + last + "\n");

            assertThat(command.rows())
                    .containsExactly(
                            ingested(2, 2, "MS-0001", "CTABCDEFGH2345", first),
                            ingested(4, 5, "MS-0003", "CTABCDEFGH2347", multiLine),
                            ingested(7, 7, "MS-0004", "CTABCDEFGH2348", last));
            assertThat(command.rejectedRows())
                    .containsExactly(
                            rejected(3, 3, FormatDefect.INVALID_TRACKING_NUMBER, List.of("tracking_number"), wrong));
            assertThat(command.receivedCount()).isEqualTo(5);
            assertThat(command.unreadable()).isNull();
        }

        @Test
        void 行25_航海のファイルの行は外部IDと同じ航海番号の航海の値で主要実績のファイルの行は主要実績の値() {
            IngestFileCommand voyage = voyages(VOYAGE_HEADER + "\n" + voyageLine("V2611E", TWO_CALLS) + "\n");
            IngestFileCommand milestone = milestones(MILESTONE_HEADER + "\n" + line("MS-0001", TRACKING_NUMBER) + "\n");

            IngestedRow voyageRow = voyage.rows().getFirst();
            assertThat(voyageRow.values()).isInstanceOf(VoyageValues.class);
            assertThat(((VoyageValues) voyageRow.values()).voyageNumber()).isEqualTo(voyageRow.externalId());
            assertThat(milestone.rows())
                    .extracting(IngestedRow::values)
                    .containsExactly(milestoneValues(TRACKING_NUMBER));
        }
    }
}
