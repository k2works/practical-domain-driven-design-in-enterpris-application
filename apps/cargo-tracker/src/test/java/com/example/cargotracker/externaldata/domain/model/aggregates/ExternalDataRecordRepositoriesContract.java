package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                null);

        receivedRecords().add(keyless);

        assertThat(storedReceivedRecord(keyless.id())).contains(keyless);
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
                range);
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
                new PhysicalLineRange(line, line));
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

        List<ReceivedRecord> first = receivedRecords().quarantinedOf(ingestion.id(), 100);
        List<ReceivedRecord> again = receivedRecords().quarantinedOf(ingestion.id(), 100);

        assertThat(first).containsExactly(fileError, oneLine, twoLines, untilEnd);
        assertThat(again).isEqualTo(first);
        assertThat(receivedRecords().quarantinedOf(ingestion.id(), 2)).containsExactly(fileError, oneLine);
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

        assertThat(receivedRecords().quarantinedOf(ingestion.id(), 100)).containsExactly(quarantined, rowError);
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
}
