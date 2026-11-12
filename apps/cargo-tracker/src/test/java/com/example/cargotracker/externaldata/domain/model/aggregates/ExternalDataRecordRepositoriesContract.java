package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedRecord;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込・外部原本・受信記録のリポジトリの契約（Bolt 39b の行 7・9b・10・12、Bolt 39b2 の行 17・18・19、Bolt 39b3 の行 12）。メモリ・H2・PostgreSQL の実装が継いで同じ約束を確かめる。
 * 取込と受信記録のポートは追記と数えること（と隔離を読む照会。Bolt 39c3）だけを持つので、書いた行の往復は継ぐ側が {@link #storedIngestion(UUID)}・
 * {@link #storedReceivedRecord(UUID)} で読む（ポートの外）。提供元の名前は乱数の UUID を含めて自分の行で絞る。
 *
 * <p>{@code @Transactional} は継ぐ側ではなくこのクラスに付ける。テストのトランザクションの属性はテストのメソッドを宣言したクラスから
 * 引かれ、継ぐ側のクラスに付けても継いだメソッドはトランザクションの外で動き、行がコミットされて共有の DB に残った（承認済みの情報源が
 * 残り、dev の開始判定の件数を崩した。Bolt 39b のステップ 4）。メモリの実装では効かない。
 */
@Transactional
public abstract class ExternalDataRecordRepositoriesContract {

    /** マイクロ秒までの時刻（DB の精度。行 10）。 */
    private static final Instant INGESTED_AT = Instant.parse("2026-11-03T01:02:03.123456Z");

    private static final Instant ACQUIRED_AT = Instant.parse("2026-11-02T23:59:58.654321Z");

    protected abstract IngestionRepository ingestions();

    protected abstract ExternalRecordRepository externalRecords();

    protected abstract ReceivedRecordRepository receivedRecords();

    /** 情報源を入れる（ポートの外。取込の外部キーの先）。 */
    protected abstract void given(DataSource source);

    /** 書いた取込を読む（ポートの外。往復の確かめ）。 */
    protected abstract Optional<Ingestion> storedIngestion(UUID id);

    /** 書いた受信記録を読む（ポートの外。往復の確かめ）。 */
    protected abstract Optional<ReceivedRecord> storedReceivedRecord(UUID id);

    /** 受信記録の判断のリポジトリ（Bolt 39d2）。 */
    protected abstract ReceivedRecordDecisionRepository receivedRecordDecisions();

    /** 書いた判断を受信記録 ID で読む（ポートの外。往復の確かめ。Bolt 39d2）。 */
    protected abstract Optional<ReceivedRecordDecision> storedDecision(UUID receivedRecordId);

    /**
     * 取込のない（{@code ingestion_id} が NULL の）キーのない隔離の受信記録をポートを通さず書き、ID を返す（Bolt 39d2 の行 22。受信記録の
     * record は取込 ID を必ず持つので、ポートでは書けない）。メモリの実装はそのような受信記録を持てないので、書かずに新しい ID を返す。
     */
    protected abstract UUID receivedRecordWithoutIngestion();

    private final String provider = "契約の提供元-" + UUID.randomUUID();

    private final DataSource source = new DataSource(UUID.randomUUID(), provider, "VOYAGE", ApprovalStatus.APPROVED);

    @BeforeEach
    void 取込の外部キーの先の情報源を入れる() {
        given(source);
    }

    private static PayloadHash hash(String content) {
        return PayloadHash.of(content.getBytes(StandardCharsets.UTF_8));
    }

    private Ingestion ingestion(int receivedCount) {
        return ingestion(receivedCount, null, 0);
    }

    private Ingestion ingestion(int receivedCount, Integer unparsedFromLine, int unparsedLineCount) {
        return new Ingestion(
                UUID.randomUUID(),
                source.id(),
                PayloadObjectKey.newIngestionFile(),
                hash("ファイル"),
                new UserId(UUID.randomUUID()),
                INGESTED_AT,
                receivedCount,
                unparsedFromLine,
                unparsedLineCount);
    }

    private ExternalRecord externalRecord(IdempotencyKey key) {
        return new ExternalRecord(
                UUID.randomUUID(),
                key,
                hash("行-" + key.externalId()),
                PayloadObjectKey.newRecordPayload(),
                INGESTED_AT);
    }

    private IdempotencyKey key(String recordType, String externalId) {
        return new IdempotencyKey(provider, recordType, externalId, "1");
    }

    private static ReceivedRecord receivedRecord(
            ExternalRecord externalRecord, Ingestion ingestion, Classification classification, String note) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                externalRecord.id(),
                ingestion.id(),
                externalRecord.payloadHash(),
                PayloadObjectKey.newRecordPayload(),
                ACQUIRED_AT,
                classification,
                note,
                INGESTED_AT,
                null,
                null);
    }

    @Test
    void 行12_件数0の取込を書いて読める() {
        Ingestion empty = ingestion(0);

        ingestions().add(empty);

        assertThat(storedIngestion(empty.id())).contains(empty);
    }

    @Test
    void 行6_取込をIDで引くと書いた取込を取り込めなかった行の始まりと数を含めて返す() {
        // Bolt 39c の行 6（結果の画面の取込の要約の照会）
        Ingestion ingestion = ingestion(5, 4, 2);
        ingestions().add(ingestion);

        assertThat(ingestions().findById(ingestion.id())).contains(ingestion);
    }

    @Test
    void 行14_書いていないIDの取込は引けない() {
        // Bolt 39c の行 14（ない取込 ID は共通の 404）
        assertThat(ingestions().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void 行10_取込の取込者と取込時刻をマイクロ秒まで往復する() {
        Ingestion ingestion = ingestion(3);

        ingestions().add(ingestion);

        assertThat(storedIngestion(ingestion.id())).hasValueSatisfying(stored -> {
            assertThat(stored.ingestedBy()).isEqualTo(ingestion.ingestedBy());
            assertThat(stored.ingestedAt()).isEqualTo(INGESTED_AT);
            assertThat(stored.receivedCount()).isEqualTo(3);
        });
    }

    @Test
    void 行10_外部原本を足すと冪等性キーで引け最初の受信時刻を往復する() {
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-001"));

        externalRecords().add(externalRecord);

        assertThat(externalRecords().findByKey(externalRecord.key())).contains(externalRecord);
    }

    @Test
    void 行7_冪等性キーは4つの列で引く() {
        ExternalRecord voyage = externalRecord(key("VOYAGE", "X-001"));
        ExternalRecord milestone = externalRecord(key("MILESTONE", "X-001"));
        externalRecords().add(voyage);
        externalRecords().add(milestone);

        assertThat(externalRecords().findByKey(voyage.key())).contains(voyage);
        assertThat(externalRecords().findByKey(milestone.key())).contains(milestone);
        assertThat(externalRecords().findByKey(new IdempotencyKey(provider, "VOYAGE", "X-001", "2")))
                .isEmpty();
        assertThat(externalRecords().findByKey(new IdempotencyKey(provider + "-別", "VOYAGE", "X-001", "1")))
                .isEmpty();
    }

    @Test
    void 行9b_行12_同じ冪等性キーの外部原本の2回目の追加は名前付きの例外() {
        IdempotencyKey key = key("VOYAGE", "V-009");
        ExternalRecordRepository repository = externalRecords();
        repository.add(externalRecord(key));
        ExternalRecord again = externalRecord(key);

        assertThatThrownBy(() -> repository.add(again)).isInstanceOf(DuplicateExternalRecordException.class);
    }

    @Test
    void 行10_受信記録を書いて読める() {
        Ingestion ingestion = ingestion(1);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-010"));
        externalRecords().add(externalRecord);
        ReceivedRecord quarantined =
                receivedRecord(externalRecord, ingestion, Classification.QUARANTINED, "前回と内容が違います");

        receivedRecords().add(quarantined);

        assertThat(storedReceivedRecord(quarantined.id())).contains(quarantined);
    }

    @Test
    void 行19_取込の取り込めなかった行の始まりと数を往復する() {
        // 40 データ行のうち 12 行目（物理行）の引用符の閉じ忘れからファイルの終わりまで 29 行を取り込めなかった
        Ingestion unclosed = ingestion(40, 12, 29);

        ingestions().add(unclosed);

        assertThat(storedIngestion(unclosed.id())).contains(unclosed);
    }

    @Test
    void 行19_取り込めなかった行のない取込は始まりがNULLで数が0() {
        Ingestion ingestion = ingestion(3);

        ingestions().add(ingestion);

        assertThat(storedIngestion(ingestion.id())).hasValueSatisfying(stored -> {
            assertThat(stored.unparsedFromLine()).isNull();
            assertThat(stored.unparsedLineCount()).isZero();
        });
    }

    @Test
    void 行19_取り込めなかった行の始まりと数の組み合わせが表のCHECKに反する取込は作れない() {
        assertThatThrownBy(() -> ingestion(3, null, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ingestion(3, 2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ingestion(3, null, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行18_外部原本と取得時刻のないキーのない隔離の受信記録を書いて読める() {
        // ファイルの誤り（必要な列がない）。データ行は 2 行目から 3 行で、すべてを取り込めず、受信記録はファイル全体の原本を指す
        Ingestion ingestion = ingestion(3, 2, 3);
        ingestions().add(ingestion);
        ReceivedRecord keyless = new ReceivedRecord(
                UUID.randomUUID(),
                null,
                ingestion.id(),
                ingestion.fileHash(),
                ingestion.fileObjectKey(),
                null,
                Classification.QUARANTINED,
                "ファイル: 必要な列がありません（acquired_at）。",
                INGESTED_AT,
                null,
                null);

        receivedRecords().add(keyless);

        assertThat(storedReceivedRecord(keyless.id())).contains(keyless);
        assertThat(receivedRecords().countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                .isEqualTo(1);
    }

    @Test
    void 行18_外部原本か取得時刻のない受信記録は隔離のほかは表のCHECKと同じく作れない() {
        UUID externalRecordId = UUID.randomUUID();
        for (Classification classification : Classification.values()) {
            if (classification == Classification.QUARANTINED) {
                continue;
            }
            assertThatThrownBy(() -> keylessReceivedRecord(null, ACQUIRED_AT, classification))
                    .as("外部原本のない %s", classification)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> keylessReceivedRecord(externalRecordId, null, classification))
                    .as("取得時刻のない %s", classification)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void 行18_外部原本と取得時刻の片方だけがない隔離の受信記録は表のCHECKと同じく作れない() {
        UUID externalRecordId = UUID.randomUUID();

        assertThatThrownBy(() -> keylessReceivedRecord(null, ACQUIRED_AT, Classification.QUARANTINED))
                .as("外部原本だけがない隔離")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> keylessReceivedRecord(externalRecordId, null, Classification.QUARANTINED))
                .as("取得時刻だけがない隔離")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 行17_上限の1000文字の注記を往復する() {
        Ingestion ingestion = ingestion(1, 2, 1);
        ingestions().add(ingestion);
        // 取込のサービスが 1000 文字で切った注記（classification_note VARCHAR(1000)）が、日本語を含めて欠けずに往復する
        String head = "ファイル: 必要な列がありません（";
        String note = head + "列".repeat(999 - head.length()) + "…";
        assertThat(note).hasSize(1000);
        ReceivedRecord keyless = new ReceivedRecord(
                UUID.randomUUID(),
                null,
                ingestion.id(),
                ingestion.fileHash(),
                ingestion.fileObjectKey(),
                null,
                Classification.QUARANTINED,
                note,
                INGESTED_AT,
                null,
                null);

        receivedRecords().add(keyless);

        assertThat(storedReceivedRecord(keyless.id())).contains(keyless);
    }

    @Test
    void Bolt39d3b_行9_足した外部原本をIDで引くと冪等性キーとハッシュとオブジェクトキーと最初の受信時刻が同じでないIDは空() {
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-039"));

        externalRecords().add(externalRecord);

        assertThat(externalRecords().findById(externalRecord.id())).contains(externalRecord);
        assertThat(externalRecords().findById(UUID.randomUUID())).isEmpty();
    }

    private static ReceivedRecord keylessReceivedRecord(
            UUID externalRecordId, Instant acquiredAt, Classification classification) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                externalRecordId,
                UUID.randomUUID(),
                hash("行"),
                PayloadObjectKey.newRecordPayload(),
                acquiredAt,
                classification,
                null,
                INGESTED_AT,
                null,
                null);
    }

    // --- Bolt 39c3 の行 1・4・7・8（受信記録の行の範囲の往復と、隔離を行の範囲の順に読む照会）。39b の同じ番号の行と名前が衝突しないよう、
    // 名前の頭に Bolt39c3_ を付ける（入れ子のクラスにすると、継ぐ側のクラスの Spring のテストの文脈を引けない） ---

    /** 行の範囲を持つキーのない隔離（範囲が null ならファイルの誤り）。 */
    private ReceivedRecord keyless(Ingestion ingestion, PhysicalLineRange range) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                null,
                ingestion.id(),
                hash("行-" + range),
                PayloadObjectKey.newRecordPayload(),
                null,
                Classification.QUARANTINED,
                "隔離-" + range,
                INGESTED_AT,
                range,
                null);
    }

    private ReceivedRecord keyed(
            ExternalRecord externalRecord, Ingestion ingestion, Classification classification, int line) {
        return new ReceivedRecord(
                UUID.randomUUID(),
                externalRecord.id(),
                ingestion.id(),
                externalRecord.payloadHash(),
                PayloadObjectKey.newRecordPayload(),
                ACQUIRED_AT,
                classification,
                classification == Classification.ADOPTED ? null : classification + "-" + line,
                INGESTED_AT,
                new PhysicalLineRange(line, line),
                null);
    }

    @Test
    void Bolt39c3_行1_受信記録の行の範囲を往復する() {
        Ingestion ingestion = ingestion(2);
        ingestions().add(ingestion);
        ReceivedRecord twoLines = keyless(ingestion, new PhysicalLineRange(2, 3));

        receivedRecords().add(twoLines);

        assertThat(storedReceivedRecord(twoLines.id())).contains(twoLines);
    }

    @Test
    void Bolt39c3_行4_行の範囲のない受信記録は範囲がNULLのまま往復する() {
        Ingestion ingestion = ingestion(2, 2, 2);
        ingestions().add(ingestion);
        ReceivedRecord fileError = keyless(ingestion, null);

        receivedRecords().add(fileError);

        assertThat(storedReceivedRecord(fileError.id()))
                .hasValueSatisfying(stored -> assertThat(stored.lineRange()).isNull());
    }

    @Test
    void Bolt39c3_行7_取込の隔離を範囲のないものを先に行の範囲の始まりの順に返しほかの取込の隔離は返さない() {
        Ingestion ingestion = ingestion(40);
        Ingestion other = ingestion(1);
        ingestions().add(ingestion);
        ingestions().add(other);
        ReceivedRecord untilEnd = keyless(ingestion, new PhysicalLineRange(12, 40));
        ReceivedRecord twoLines = keyless(ingestion, new PhysicalLineRange(5, 6));
        ReceivedRecord oneLine = keyless(ingestion, new PhysicalLineRange(3, 3));
        ReceivedRecord fileError = keyless(ingestion, null);
        // 期待の順と逆に書く（T-229）
        receivedRecords().add(untilEnd);
        receivedRecords().add(twoLines);
        receivedRecords().add(oneLine);
        receivedRecords().add(fileError);
        receivedRecords().add(keyless(other, new PhysicalLineRange(2, 2)));

        List<ReceivedRecord> first = receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 100);
        List<ReceivedRecord> again = receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 100);

        assertThat(first).containsExactly(fileError, oneLine, twoLines, untilEnd);
        assertThat(again).isEqualTo(first);
        assertThat(receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 2))
                .containsExactly(fileError, oneLine);
    }

    @Test
    void Bolt39c3_行8_隔離の照会は採用と重複の受信記録を返さない() {
        Ingestion ingestion = ingestion(4);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39C3"));
        externalRecords().add(externalRecord);
        ReceivedRecord adopted = keyed(externalRecord, ingestion, Classification.ADOPTED, 2);
        ReceivedRecord duplicate = keyed(externalRecord, ingestion, Classification.DUPLICATE, 3);
        ReceivedRecord quarantined = keyed(externalRecord, ingestion, Classification.QUARANTINED, 4);
        ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(5, 5));
        receivedRecords().add(adopted);
        receivedRecords().add(duplicate);
        receivedRecords().add(quarantined);
        receivedRecords().add(rowError);

        assertThat(receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 100))
                .containsExactly(quarantined, rowError);
    }

    @Test
    void Bolt39h_行8_取込の隔離の照会は差異も行の順に返す() {
        // S-16 の「隔離・差異の理由」（開始準備の決定 (7)）。判断を待つ一覧と件数に差異が入るのは Bolt 39h2 の行 4 の組
        Ingestion ingestion = ingestion(4);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("MILESTONE", "MS-39H"));
        externalRecords().add(externalRecord);
        ReceivedRecord quarantined = keyed(externalRecord, ingestion, Classification.QUARANTINED, 4);
        ReceivedRecord discrepancy = keyed(externalRecord, ingestion, Classification.DISCREPANCY, 3);
        ReceivedRecord adopted = keyed(externalRecord, ingestion, Classification.ADOPTED, 2);
        receivedRecords().add(quarantined);
        receivedRecords().add(discrepancy);
        receivedRecords().add(adopted);

        assertThat(receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 100))
                .containsExactly(discrepancy, quarantined);
        assertThat(receivedRecords().countByIngestionAndClassification(ingestion.id(), Classification.DISCREPANCY))
                .isEqualTo(1);
    }

    // --- Bolt 39d の行 3・4・6・15（S-17 の判断を待つ隔離の一覧の照会。開始準備の決定 (3)・(4)）。名前の頭に Bolt39d_ を付ける。
    // 絞らない照会は共有の DB のほかの隔離も返すので、自分の取込の時刻をほかより新しい未来に置き、先頭の行で確かめる。行 7（判断の表に行のある
    // 隔離）は Bolt 39d4 の行 22（判断のポートで書く）に上げた ---

    /** ほかのテストのどの取込より新しい時刻（絞らない照会の先頭に自分の隔離が来る）。 */
    private static final Instant FUTURE = Instant.parse("2099-01-01T00:00:00.123456Z");

    private Ingestion ingestionAt(Instant ingestedAt) {
        return ingestionAt(UUID.randomUUID(), ingestedAt);
    }

    private Ingestion ingestionAt(UUID id, Instant ingestedAt) {
        return new Ingestion(
                id,
                source.id(),
                PayloadObjectKey.newIngestionFile(),
                hash("ファイル"),
                new UserId(UUID.randomUUID()),
                ingestedAt,
                3,
                null,
                0);
    }

    /** 先頭の 1 文字を決めた乱数の UUID（同じ時刻の取込を取込 ID の順に並べる確かめ。符号つきの比べ方と順が違う 8〜f を含める）。 */
    private static UUID uuidStartingWith(char head) {
        return UUID.fromString(head + UUID.randomUUID().toString().substring(1));
    }

    /** 受信記録から期待する判断を待つ隔離（外部原本のないときは外部 ID・外部版が null）。 */
    private UndecidedRecord undecided(ReceivedRecord received, Ingestion ingestion, ExternalRecord externalRecord) {
        return new UndecidedRecord(
                received.id(),
                received.classification(),
                ingestion.id(),
                ingestion.ingestedAt(),
                ingestion.dataSourceId(),
                received.lineRange(),
                externalRecord == null ? null : externalRecord.key().externalId(),
                externalRecord == null ? null : externalRecord.key().externalVersion(),
                received.classificationNote());
    }

    @Test
    void Bolt39d_行3_判断を待つ隔離の照会と件数は採用と重複の受信記録を含めない() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D-3"));
        externalRecords().add(externalRecord);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.ADOPTED, 2));
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.DUPLICATE, 3));
        ReceivedRecord quarantined = keyed(externalRecord, ingestion, Classification.QUARANTINED, 4);
        ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(5, 5));
        receivedRecords().add(quarantined);
        receivedRecords().add(rowError);

        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .containsExactly(
                        undecided(quarantined, ingestion, externalRecord), undecided(rowError, ingestion, null));
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(2);
    }

    @Test
    void Bolt39d_行4_判断を待つ隔離は取込の新しい順で同じ時刻は取込IDの順で同じ取込はファイルの誤りを先に行の範囲の順() {
        // 書く順を期待の順と逆にする（T-245）: 古い取込から書き、同じ取込の受信記録は行の範囲の逆の順に書く
        Ingestion older = ingestionAt(FUTURE.minusSeconds(60));
        Ingestion sameTimeLater = ingestionAt(uuidStartingWith('c'), FUTURE);
        Ingestion sameTimeEarlier = ingestionAt(uuidStartingWith('1'), FUTURE);
        ingestions().add(older);
        ingestions().add(sameTimeLater);
        ingestions().add(sameTimeEarlier);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D-4"));
        externalRecords().add(externalRecord);
        ReceivedRecord olderRow = keyless(older, new PhysicalLineRange(2, 2));
        ReceivedRecord laterRow = keyless(sameTimeLater, new PhysicalLineRange(2, 2));
        ReceivedRecord untilEnd = keyless(sameTimeEarlier, new PhysicalLineRange(12, 40));
        ReceivedRecord mismatch = keyed(externalRecord, sameTimeEarlier, Classification.QUARANTINED, 5);
        ReceivedRecord twoLines = keyless(sameTimeEarlier, new PhysicalLineRange(3, 4));
        ReceivedRecord fileError = keyless(sameTimeEarlier, null);
        receivedRecords().add(olderRow);
        receivedRecords().add(laterRow);
        receivedRecords().add(untilEnd);
        receivedRecords().add(mismatch);
        receivedRecords().add(twoLines);
        receivedRecords().add(fileError);
        List<UndecidedRecord> expected = List.of(
                undecided(fileError, sameTimeEarlier, null),
                undecided(twoLines, sameTimeEarlier, null),
                undecided(mismatch, sameTimeEarlier, externalRecord),
                undecided(untilEnd, sameTimeEarlier, null),
                undecided(laterRow, sameTimeLater, null),
                undecided(olderRow, older, null));

        assertThat(receivedRecords().undecidedRecords(null, 0, 6)).isEqualTo(expected);
        assertThat(receivedRecords().undecidedRecords(null, 2, 3)).isEqualTo(expected.subList(2, 5));
    }

    @Test
    void Bolt39d_行6_取込で絞るとその取込の隔離だけを返し数える() {
        Ingestion ingestion = ingestionAt(FUTURE);
        Ingestion other = ingestionAt(FUTURE.plusSeconds(1));
        ingestions().add(ingestion);
        ingestions().add(other);
        ReceivedRecord first = keyless(ingestion, new PhysicalLineRange(2, 2));
        ReceivedRecord second = keyless(ingestion, new PhysicalLineRange(3, 3));
        receivedRecords().add(first);
        receivedRecords().add(keyless(other, new PhysicalLineRange(2, 2)));
        receivedRecords().add(second);

        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .containsExactly(undecided(first, ingestion, null), undecided(second, ingestion, null));
        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 1, 100))
                .containsExactly(undecided(second, ingestion, null));
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(2);
        assertThat(receivedRecords().countUndecidedRecords(other.id())).isEqualTo(1);
        assertThat(receivedRecords().countUndecidedRecords(UUID.randomUUID())).isZero();
    }

    @Test
    void Bolt39d_行15_読んでいる間に取込が増えると読み直したときに新しい取込の隔離が先頭に出る() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord existing = keyless(ingestion, new PhysicalLineRange(2, 2));
        receivedRecords().add(existing);
        List<UndecidedRecord> before = receivedRecords().undecidedRecords(null, 0, 1);
        int countBefore = receivedRecords().countUndecidedRecords(null);
        Ingestion newer = ingestionAt(FUTURE.plusSeconds(60));
        ingestions().add(newer);
        ReceivedRecord added = keyless(newer, new PhysicalLineRange(2, 2));
        receivedRecords().add(added);

        List<UndecidedRecord> after = receivedRecords().undecidedRecords(null, 0, 2);

        assertThat(before).containsExactly(undecided(existing, ingestion, null));
        assertThat(after).containsExactly(undecided(added, newer, null), undecided(existing, ingestion, null));
        assertThat(receivedRecords().countUndecidedRecords(null)).isEqualTo(countBefore + 1);
    }

    @Test
    void 行11_受信記録を取込と分類ごとに数える() {
        Ingestion ingestion = ingestion(3);
        Ingestion other = ingestion(1);
        ingestions().add(ingestion);
        ingestions().add(other);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-011"));
        externalRecords().add(externalRecord);

        receivedRecords().add(receivedRecord(externalRecord, ingestion, Classification.ADOPTED, null));
        receivedRecords().add(receivedRecord(externalRecord, ingestion, Classification.DUPLICATE, null));
        receivedRecords().add(receivedRecord(externalRecord, ingestion, Classification.DUPLICATE, null));
        receivedRecords().add(receivedRecord(externalRecord, other, Classification.DUPLICATE, null));

        assertThat(receivedRecords().countByIngestionAndClassification(ingestion.id(), Classification.ADOPTED))
                .isEqualTo(1);
        assertThat(receivedRecords().countByIngestionAndClassification(ingestion.id(), Classification.DUPLICATE))
                .isEqualTo(2);
        assertThat(receivedRecords().countByIngestionAndClassification(ingestion.id(), Classification.QUARANTINED))
                .isZero();
        assertThat(receivedRecords().countByIngestionAndClassification(other.id(), Classification.DUPLICATE))
                .isEqualTo(1);
    }

    // --- Bolt 39d2 の行 2・4〜6・21・22（受信記録の判断のポートと、受信記録を ID で引く照会。開始準備の決定 (2)・(3)）。名前の頭に Bolt39d2_ を
    // 付ける。行 7 を判断のポートで書き直すことは Bolt 39d4 の行 22 ---

    /** 補助文字（U+20BB7。UTF-16 で 2 単位）。 */
    private static final String SUPPLEMENTARY = new String(Character.toChars(0x20BB7));

    /** マイクロ秒までの判断の時刻（DB の精度）。 */
    private static final Instant DECIDED_AT = Instant.parse("2099-01-01T00:10:00.654321Z");

    private final UserId steward = new UserId(UUID.randomUUID());

    @Test
    void Bolt39d2_行4_受信記録をIDで引くと取込で書いた受信記録を分類によらず返し書いていないIDは空() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D2-4"));
        externalRecords().add(externalRecord);
        ReceivedRecord adopted = keyed(externalRecord, ingestion, Classification.ADOPTED, 2);
        ReceivedRecord rowError = keyless(ingestion, new PhysicalLineRange(3, 3));
        receivedRecords().add(adopted);
        receivedRecords().add(rowError);

        assertThat(receivedRecords().findById(adopted.id())).contains(adopted);
        assertThat(receivedRecords().findById(rowError.id())).contains(rowError);
        assertThat(receivedRecords().findById(UUID.randomUUID())).isEmpty();
        assertThat(receivedRecords().undecidedRecord(UUID.randomUUID())).isEmpty();
    }

    @Test
    void Bolt39d2_行4_IDで絞った照会は判断を待つ隔離の一覧と同じ中身を返し隔離でない受信記録は空() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D2-4B"));
        externalRecords().add(externalRecord);
        ReceivedRecord duplicate = keyed(externalRecord, ingestion, Classification.DUPLICATE, 2);
        ReceivedRecord mismatch = keyed(externalRecord, ingestion, Classification.QUARANTINED, 3);
        ReceivedRecord fileError = keyless(ingestion, null);
        receivedRecords().add(duplicate);
        receivedRecords().add(mismatch);
        receivedRecords().add(fileError);

        assertThat(receivedRecords().undecidedRecord(mismatch.id()))
                .contains(undecided(mismatch, ingestion, externalRecord));
        assertThat(receivedRecords().undecidedRecord(fileError.id())).contains(undecided(fileError, ingestion, null));
        assertThat(receivedRecords().undecidedRecord(duplicate.id())).isEmpty();
    }

    @Test
    void Bolt39d2_行5_判断を追記すると判断があると分かりIDで絞った照会と一覧と件数から消える() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord decided = keyless(ingestion, new PhysicalLineRange(2, 2));
        ReceivedRecord undecided = keyless(ingestion, new PhysicalLineRange(3, 3));
        receivedRecords().add(decided);
        receivedRecords().add(undecided);
        assertThat(receivedRecords().undecidedRecord(decided.id())).isPresent();
        assertThat(receivedRecordDecisions().existsFor(decided.id())).isFalse();

        receivedRecordDecisions().add(ReceivedRecordDecision.reject(decided, "提供元に確かめた。", steward, DECIDED_AT));

        assertThat(receivedRecordDecisions().existsFor(decided.id())).isTrue();
        assertThat(receivedRecordDecisions().existsFor(undecided.id())).isFalse();
        assertThat(receivedRecords().undecidedRecord(decided.id())).isEmpty();
        assertThat(receivedRecords().undecidedRecord(undecided.id())).isPresent();
        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .extracting(UndecidedRecord::receivedRecordId)
                .containsExactly(undecided.id());
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(1);
        // 判断の後も受信記録は追記専用のまま ID で引ける（判断できるかは分類と判断の有無で決める）
        assertThat(receivedRecords().findById(decided.id())).contains(decided);
    }

    @Test
    void Bolt39d2_行2_判断を判断者と判断の時刻と理由ごとマイクロ秒まで往復する() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord quarantined = keyless(ingestion, new PhysicalLineRange(2, 2));
        receivedRecords().add(quarantined);
        ReceivedRecordDecision decision =
                ReceivedRecordDecision.reject(quarantined, "直したファイルを取り込み直した。", steward, DECIDED_AT);

        receivedRecordDecisions().add(decision);

        assertThat(storedDecision(quarantined.id())).contains(decision);
    }

    @Test
    void Bolt39d2_行6_同じ受信記録の2つ目の判断は名前付きの例外になり判断は1つのまま() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord quarantined = keyless(ingestion, new PhysicalLineRange(2, 2));
        receivedRecords().add(quarantined);
        ReceivedRecordDecision first = ReceivedRecordDecision.reject(quarantined, "先の判断。", steward, DECIDED_AT);
        receivedRecordDecisions().add(first);
        ReceivedRecordDecision second =
                ReceivedRecordDecision.reject(quarantined, "後の判断。", new UserId(UUID.randomUUID()), DECIDED_AT);

        ReceivedRecordDecisionRepository decisions = receivedRecordDecisions();
        assertThatThrownBy(() -> decisions.add(second))
                .isInstanceOf(DuplicateReceivedRecordDecisionException.class)
                .hasMessageContaining(quarantined.id().toString());
        assertThat(storedDecision(quarantined.id())).contains(first);
        assertThat(receivedRecords().undecidedRecord(quarantined.id())).isEmpty();
    }

    @Test
    void Bolt39d2_行21_補助文字を含む1000文字の理由の判断を往復する() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord quarantined = keyless(ingestion, new PhysicalLineRange(2, 2));
        receivedRecords().add(quarantined);
        String reason = SUPPLEMENTARY.repeat(1_000);
        ReceivedRecordDecision decision = ReceivedRecordDecision.reject(quarantined, reason, steward, DECIDED_AT);

        receivedRecordDecisions().add(decision);

        assertThat(storedDecision(quarantined.id())).hasValueSatisfying(stored -> {
            assertThat(stored.reason()).isEqualTo(reason);
            assertThat(stored.reason().codePointCount(0, stored.reason().length()))
                    .isEqualTo(1_000);
        });
    }

    @Test
    void Bolt39d2_行22_取込のない受信記録はIDで引けずIDで絞った照会にも出ない() {
        UUID withoutIngestion = receivedRecordWithoutIngestion();

        assertThat(receivedRecords().findById(withoutIngestion)).isEmpty();
        assertThat(receivedRecords().undecidedRecord(withoutIngestion)).isEmpty();
    }

    // --- Bolt 39d4 の行 13・14（外部原本の基準の付け替え。開始準備の決定 (3)・(4)）。名前の頭に Bolt39d4_ を付ける ---

    @Test
    void Bolt39d4_行13_付け替えるとハッシュとオブジェクトキーだけが変わり冪等性キーと最初の受信時刻は同じでほかの外部原本は変わらない() {
        ExternalRecord target = externalRecord(key("VOYAGE", "V-39D4-13"));
        ExternalRecord other = externalRecord(key("VOYAGE", "V-39D4-13B"));
        externalRecords().add(target);
        externalRecords().add(other);
        PayloadHash adoptedHash = hash("採用した payload");
        PayloadObjectKey adoptedObjectKey = PayloadObjectKey.newRecordPayload();

        externalRecords().changeAdoptedPayload(target.id(), target.payloadHash(), adoptedHash, adoptedObjectKey);

        ExternalRecord expected =
                new ExternalRecord(target.id(), target.key(), adoptedHash, adoptedObjectKey, target.firstReceivedAt());
        assertThat(externalRecords().findByKey(target.key())).contains(expected);
        assertThat(externalRecords().findById(target.id())).contains(expected);
        assertThat(externalRecords().findByKey(other.key())).contains(other);
    }

    @ParameterizedTest(name = "ない ID {0}")
    @ValueSource(booleans = {false, true})
    void Bolt39d4_行14_条件のハッシュが違うかないIDなら名前付きの例外になり行は変わらない(boolean unknownId) {
        ExternalRecord target = externalRecord(key("VOYAGE", "V-39D4-14"));
        externalRecords().add(target);
        UUID id = unknownId ? UUID.randomUUID() : target.id();
        PayloadHash expectedHash = unknownId ? target.payloadHash() : hash("読んだ後に付け替えられた payload");
        PayloadHash adoptedHash = hash("採用した payload");
        PayloadObjectKey adoptedObjectKey = PayloadObjectKey.newRecordPayload();
        ExternalRecordRepository repository = externalRecords();

        assertThatThrownBy(() -> repository.changeAdoptedPayload(id, expectedHash, adoptedHash, adoptedObjectKey))
                .isInstanceOf(ConcurrentExternalRecordUpdateException.class)
                .hasMessageContaining(id.toString());
        assertThat(externalRecords().findById(target.id())).contains(target);
    }

    // --- Bolt 39d4 の行 22（39d の行 7。判断のポートで書き、3 つの実装で確かめる。開始準備の決定 (11)）。絞らない件数と照会は共有の DB のほかの隔離も含むので、差と自分の受信記録だけを確かめる
    // ---

    @Test
    void Bolt39d4_行22_判断のポートで却下を追記した隔離は取込で絞った一覧と件数と絞らない一覧と件数から消える() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord decided = keyless(ingestion, new PhysicalLineRange(2, 2));
        ReceivedRecord undecided = keyless(ingestion, new PhysicalLineRange(3, 3));
        receivedRecords().add(decided);
        receivedRecords().add(undecided);
        int allBefore = receivedRecords().countUndecidedRecords(null);

        receivedRecordDecisions().add(ReceivedRecordDecision.reject(decided, "提供元に確かめた。", steward, DECIDED_AT));

        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .extracting(UndecidedRecord::receivedRecordId)
                .containsExactly(undecided.id());
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(1);
        assertThat(receivedRecords().countUndecidedRecords(null)).isEqualTo(allBefore - 1);
        assertThat(receivedRecords().undecidedRecords(null, 0, 2))
                .extracting(UndecidedRecord::receivedRecordId)
                .contains(undecided.id())
                .doesNotContain(decided.id());
    }

    // --- Bolt 39d2b の行 5（まとめての却下の対象の照会と件数。開始準備の決定 (2)、T-240・T-245）。名前の頭に Bolt39d2b_ を付ける。
    // 39d の行 7 を判断のポートで書き直すことは Bolt 39d4 の行 22 ---

    @Test
    void Bolt39d2b_行5_対象は1つの取込の判断を待つキーのない隔離だけでファイルの誤りを先に行の範囲の順() {
        Ingestion ingestion = ingestionAt(FUTURE);
        Ingestion other = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ingestions().add(other);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D2B-5"));
        externalRecords().add(externalRecord);
        ReceivedRecord untilEnd = keyless(ingestion, new PhysicalLineRange(12, 40));
        ReceivedRecord twoLines = keyless(ingestion, new PhysicalLineRange(5, 6));
        ReceivedRecord oneLine = keyless(ingestion, new PhysicalLineRange(3, 3));
        ReceivedRecord fileError = keyless(ingestion, null);
        ReceivedRecord decided = keyless(ingestion, new PhysicalLineRange(8, 8));
        // 期待の順と逆に書く（T-245）
        receivedRecords().add(untilEnd);
        receivedRecords().add(twoLines);
        receivedRecords().add(oneLine);
        receivedRecords().add(fileError);
        receivedRecords().add(decided);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.QUARANTINED, 2));
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.ADOPTED, 9));
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.DUPLICATE, 10));
        receivedRecords().add(keyless(other, new PhysicalLineRange(2, 2)));
        receivedRecordDecisions().add(ReceivedRecordDecision.reject(decided, "先に 1 件ずつ却下した。", steward, DECIDED_AT));

        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(ingestion.id()))
                .containsExactly(fileError, oneLine, twoLines, untilEnd);
        assertThat(receivedRecords().countUndecidedKeylessQuarantines(ingestion.id()))
                .isEqualTo(4);
    }

    @Test
    void Bolt39d2b_行5_キーのない隔離のない取込とない取込は空で0件() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D2B-5B"));
        externalRecords().add(externalRecord);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.QUARANTINED, 2));

        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(ingestion.id()))
                .isEmpty();
        assertThat(receivedRecords().countUndecidedKeylessQuarantines(ingestion.id()))
                .isZero();
        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(UUID.randomUUID()))
                .isEmpty();
        assertThat(receivedRecords().countUndecidedKeylessQuarantines(UUID.randomUUID()))
                .isZero();
    }

    // --- Bolt 39h2 の行 4・5（判断を待つ隔離・差異の一覧の照会。開始準備の決定 (4)）---

    @Test
    void Bolt39h2_行4_判断を待つ一覧と件数は隔離と差異を行の順に混ぜて返し分類を持つ() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("MILESTONE", "MS-39H2-4A"));
        externalRecords().add(externalRecord);
        // 期待の順と逆に書く（T-245）
        ReceivedRecord discrepancy = keyed(externalRecord, ingestion, Classification.DISCREPANCY, 6);
        ReceivedRecord quarantined = keyed(externalRecord, ingestion, Classification.QUARANTINED, 4);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.DUPLICATE, 5));
        receivedRecords().add(discrepancy);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.ADOPTED, 2));
        receivedRecords().add(quarantined);

        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .containsExactly(
                        undecided(quarantined, ingestion, externalRecord),
                        undecided(discrepancy, ingestion, externalRecord));
        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .extracting(UndecidedRecord::classification)
                .containsExactly(Classification.QUARANTINED, Classification.DISCREPANCY);
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(2);
    }

    @Test
    void Bolt39h2_行4_却下した差異は一覧から消え同じ原本を送り直した差異だけが出る() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39H2-4B"));
        externalRecords().add(externalRecord);
        ReceivedRecord first = keyed(externalRecord, ingestion, Classification.DISCREPANCY, 2);
        ReceivedRecord resent = keyed(externalRecord, ingestion, Classification.DISCREPANCY, 3);
        receivedRecords().add(first);

        receivedRecordDecisions().add(ReceivedRecordDecision.reject(first, "古い外部版と分かった。", steward, DECIDED_AT));
        receivedRecords().add(resent);

        assertThat(receivedRecords().undecidedRecord(first.id())).isEmpty();
        assertThat(receivedRecords().undecidedRecords(ingestion.id(), 0, 100))
                .containsExactly(undecided(resent, ingestion, externalRecord));
        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(1);
    }

    @Test
    void Bolt39h2_行4_受信記録IDで引くと差異は分類つきで返し採用と重複は空() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39H2-4E"));
        externalRecords().add(externalRecord);
        ReceivedRecord discrepancy = keyed(externalRecord, ingestion, Classification.DISCREPANCY, 2);
        ReceivedRecord adopted = keyed(externalRecord, ingestion, Classification.ADOPTED, 3);
        receivedRecords().add(discrepancy);
        receivedRecords().add(adopted);

        assertThat(receivedRecords().undecidedRecord(discrepancy.id()))
                .contains(undecided(discrepancy, ingestion, externalRecord));
        assertThat(receivedRecords().undecidedRecord(adopted.id())).isEmpty();
    }

    @Test
    void Bolt39h2_行4_取込で絞る絞らないは今のまま差異も同じ定めで絞る() {
        Ingestion ingestion = ingestionAt(FUTURE);
        Ingestion other = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ingestions().add(other);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39H2-4D"));
        externalRecords().add(externalRecord);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.DISCREPANCY, 2));
        receivedRecords().add(keyed(externalRecord, other, Classification.DISCREPANCY, 2));
        int all = receivedRecords().countUndecidedRecords(null);

        assertThat(receivedRecords().countUndecidedRecords(ingestion.id())).isEqualTo(1);
        assertThat(receivedRecords().countUndecidedRecords(other.id())).isEqualTo(1);
        assertThat(all).isGreaterThanOrEqualTo(2);
    }

    @Test
    void Bolt39h2_行5_キーのない隔離の対象に差異は入らない() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39H2-5"));
        externalRecords().add(externalRecord);
        ReceivedRecord rowError1 = keyless(ingestion, new PhysicalLineRange(3, 3));
        ReceivedRecord rowError2 = keyless(ingestion, new PhysicalLineRange(7, 7));
        receivedRecords().add(rowError1);
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.QUARANTINED, 4));
        receivedRecords().add(keyed(externalRecord, ingestion, Classification.DISCREPANCY, 5));
        receivedRecords().add(rowError2);

        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(ingestion.id()))
                .containsExactly(rowError1, rowError2);
        assertThat(receivedRecords().countUndecidedKeylessQuarantines(ingestion.id()))
                .isEqualTo(2);
    }

    /** 分類のすべてと外部原本の有無の組（受信記録の不変条件が外部原本のない受信記録を隔離だけに絞るので、作れる組だけ。T-240）。 */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> classificationsAndExternalRecord() {
        return java.util.Arrays.stream(Classification.values())
                .flatMap(classification -> java.util.stream.Stream.of(true, false)
                        .filter(hasExternalRecord -> hasExternalRecord || classification == Classification.QUARANTINED)
                        .map(hasExternalRecord ->
                                org.junit.jupiter.params.provider.Arguments.of(classification, hasExternalRecord)));
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} 外部原本 {1}")
    @org.junit.jupiter.params.provider.MethodSource("classificationsAndExternalRecord")
    void Bolt39d2b_行5_対象に入るのは外部原本のない隔離だけ(Classification classification, boolean hasExternalRecord) {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ReceivedRecord received;
        if (hasExternalRecord) {
            ExternalRecord externalRecord = externalRecord(key("VOYAGE", "V-39D2B-" + UUID.randomUUID()));
            externalRecords().add(externalRecord);
            received = keyed(externalRecord, ingestion, classification, 2);
        } else {
            received = keyless(ingestion, new PhysicalLineRange(2, 2));
        }
        receivedRecords().add(received);
        boolean target = classification == Classification.QUARANTINED && !hasExternalRecord;

        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(ingestion.id()))
                .isEqualTo(target ? List.of(received) : List.of());
        assertThat(receivedRecords().countUndecidedKeylessQuarantines(ingestion.id()))
                .isEqualTo(target ? 1 : 0);
    }

    @Test
    void Bolt39d2b_行5_作れる組は分類のすべてと外部原本のない隔離を含む() {
        assertThat(classificationsAndExternalRecord().map(arguments -> arguments.get()[0]))
                .containsAll(List.of(Classification.values()));
        assertThat(classificationsAndExternalRecord().map(arguments -> List.of(arguments.get())))
                .contains(List.of(Classification.QUARANTINED, false));
    }

    // --- Bolt 39d3 の行 4・5（受信記録の値の不変条件と往復。開始準備の決定 (3)）。上限いっぱい（寄港 60・補助文字を含む 30 文字の航海番号）の
    // 往復は Bolt 39d3c の行 5 ---

    /**
     * Bolt 39d3c の行 5（上限いっぱいの往復。開始準備の決定 (8)、T-242）。寄港 60 で到着と出発の両方を持つ航海の要約（3719 文字）と、補助文字を
     * 含む 30 文字（コードポイント）の航海番号の受信記録の値が、追記と ID の照会・取込の隔離の照会で往復する（H2 を広げない判断の確かめ。
     * ADR-007）。
     */
    @Test
    void Bolt39d3c_行5_上限いっぱいの航海の受信記録の値が往復する() {
        String voyageNumber = "V"
                + "𠮷"
                        .repeat(com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues
                                        .VOYAGE_NUMBER_MAX_LENGTH
                                - 1);
        List<com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue> portCalls =
                java.util.stream.IntStream.range(
                                0,
                                com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues
                                        .MAX_PORT_CALLS)
                        .mapToObj(
                                i -> new com.example.cargotracker.externaldata.domain.model.valueobjects.PortCallValue(
                                        new com.example.cargotracker.shared.domain.Location("JPTYO"),
                                        new com.example.cargotracker.shared.domain.UtcInstant(
                                                java.time.Instant.parse("2026-12-01T00:00:00.123456Z")
                                                        .plusSeconds(7200L * i)),
                                        new com.example.cargotracker.shared.domain.UtcInstant(
                                                java.time.Instant.parse("2026-12-01T01:00:00.654321Z")
                                                        .plusSeconds(7200L * i))))
                        .toList();
        ReceivedValue value =
                ReceivedValue.of(new com.example.cargotracker.externaldata.domain.model.valueobjects.VoyageValues(
                        voyageNumber, portCalls));
        assertThat(value.summary()).hasSize(3719);
        assertThat(value.targetKey().codePointCount(0, value.targetKey().length()))
                .isEqualTo(30);
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord voyage = externalRecord(key("VOYAGE", "V-39D3C-5-" + UUID.randomUUID()));
        externalRecords().add(voyage);
        ReceivedRecord quarantined = valued(voyage, ingestion, Classification.QUARANTINED, 2, value);

        receivedRecords().add(quarantined);

        assertThat(receivedRecords().findById(quarantined.id()))
                .hasValueSatisfying(found -> assertThat(found.value()).isEqualTo(value));
        assertThat(receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 10))
                .singleElement()
                .satisfies(found -> assertThat(found.value()).isEqualTo(value));
    }

    /** 寄港 2 つの航海の値（{@code VoyageValues#summary()} の形。T-230）。 */
    private static final ReceivedValue VOYAGE_VALUE =
            new ReceivedValue("V2611E", "JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;");

    /** 主要実績の値（{@code MilestoneValues#summary()} の形。T-230）。 */
    private static final ReceivedValue MILESTONE_VALUE =
            new ReceivedValue("CTABCDEFGH2345", "DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z");

    private ReceivedRecord valued(
            ExternalRecord externalRecord,
            Ingestion ingestion,
            Classification classification,
            int line,
            ReceivedValue value) {
        ReceivedRecord received = keyed(externalRecord, ingestion, classification, line);
        return new ReceivedRecord(
                received.id(),
                received.externalRecordId(),
                received.ingestionId(),
                received.payloadHash(),
                received.payloadObjectKey(),
                received.acquiredAt(),
                received.classification(),
                received.classificationNote(),
                received.classifiedAt(),
                received.lineRange(),
                value);
    }

    @Test
    void Bolt39d3_行5_航海と主要実績の受信記録の値をIDで引く照会と取込の隔離の照会で往復する() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord voyage = externalRecord(key("VOYAGE", "V-39D3-5A"));
        ExternalRecord milestone = externalRecord(key("MILESTONE", "MS-39D3-5A"));
        externalRecords().add(voyage);
        externalRecords().add(milestone);
        ReceivedRecord adopted = valued(voyage, ingestion, Classification.ADOPTED, 2, VOYAGE_VALUE);
        ReceivedRecord quarantined = valued(milestone, ingestion, Classification.QUARANTINED, 3, MILESTONE_VALUE);
        ReceivedRecord keyless = keyless(ingestion, new PhysicalLineRange(4, 4));

        receivedRecords().add(adopted);
        receivedRecords().add(quarantined);
        receivedRecords().add(keyless);

        assertThat(receivedRecords().findById(adopted.id())).contains(adopted);
        assertThat(receivedRecords().findById(quarantined.id())).contains(quarantined);
        assertThat(receivedRecords().quarantinesAndDiscrepanciesOf(ingestion.id(), 10))
                .containsExactly(quarantined, keyless);
        assertThat(receivedRecords().undecidedKeylessQuarantinesOf(ingestion.id()))
                .singleElement()
                .satisfies(found -> assertThat(found.value()).isNull());
    }

    @Test
    void Bolt39d3_行5_値を記録する前の形の外部原本を持つ受信記録は値なしで往復する() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ingestions().add(ingestion);
        ExternalRecord voyage = externalRecord(key("VOYAGE", "V-39D3-5B"));
        externalRecords().add(voyage);
        ReceivedRecord before = keyed(voyage, ingestion, Classification.QUARANTINED, 2);

        receivedRecords().add(before);

        assertThat(receivedRecords().findById(before.id()))
                .hasValueSatisfying(found -> assertThat(found.value()).isNull());
    }

    @Test
    void Bolt39d3_行4_外部原本を持たない受信記録に値を渡すと表のCHECKと同じく作れない() {
        Ingestion ingestion = ingestionAt(FUTURE);
        ReceivedRecord keyless = keyless(ingestion, new PhysicalLineRange(2, 2));
        UUID id = keyless.id();
        UUID ingestionId = keyless.ingestionId();
        PayloadHash payloadHash = keyless.payloadHash();
        PayloadObjectKey payloadObjectKey = keyless.payloadObjectKey();
        String note = keyless.classificationNote();
        Instant classifiedAt = keyless.classifiedAt();
        PhysicalLineRange lineRange = keyless.lineRange();

        assertThatThrownBy(() -> new ReceivedRecord(
                        id,
                        null,
                        ingestionId,
                        payloadHash,
                        payloadObjectKey,
                        null,
                        Classification.QUARANTINED,
                        note,
                        classifiedAt,
                        lineRange,
                        VOYAGE_VALUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Bolt39d3_行4_受信記録の値はキーと要約を必ず持ち要約は採用値と同じ4000コードポイントまで() {
        String supplementary = new String(Character.toChars(0x20BB7));
        String limit = supplementary.repeat(AdoptedValue.VALUE_SUMMARY_MAX_LENGTH);

        assertThat(new ReceivedValue("V2611E", limit).summary()).isEqualTo(limit);
        assertThatThrownBy(() -> new ReceivedValue("V2611E", limit + "A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReceivedValue(null, "要約")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReceivedValue("V2611E", null)).isInstanceOf(NullPointerException.class);
    }
}
