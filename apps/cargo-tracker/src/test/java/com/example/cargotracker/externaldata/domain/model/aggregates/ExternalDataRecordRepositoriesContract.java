package com.example.cargotracker.externaldata.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.externaldata.domain.model.aggregates.DataSource.ApprovalStatus;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.shared.domain.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取込・外部原本・受信記録のリポジトリの契約（Bolt 39b の行 7・9b・10・12）。メモリ・H2・PostgreSQL の実装が継いで同じ約束を確かめる。
 * 取込と受信記録のポートは追記と数えることだけを持つので、書いた行の往復は継ぐ側が {@link #storedIngestion(UUID)}・
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
        return new Ingestion(
                UUID.randomUUID(),
                source.id(),
                PayloadObjectKey.newIngestionFile(),
                hash("ファイル"),
                new UserId(UUID.randomUUID()),
                INGESTED_AT,
                receivedCount);
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
                INGESTED_AT);
    }

    @Test
    void 行12_件数0の取込を書いて読める() {
        Ingestion empty = ingestion(0);

        ingestions().add(empty);

        assertThat(storedIngestion(empty.id())).contains(empty);
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
        ExternalRecord record = externalRecord(key("VOYAGE", "V-001"));

        externalRecords().add(record);

        assertThat(externalRecords().findByKey(record.key())).contains(record);
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
    void 行9b_同じ冪等性キーの外部原本の2回目の追加は一意制約の違反() {
        IdempotencyKey key = key("VOYAGE", "V-009");
        externalRecords().add(externalRecord(key));

        assertThatThrownBy(() -> externalRecords().add(externalRecord(key))).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 行10_受信記録を書いて読める() {
        Ingestion ingestion = ingestion(1);
        ingestions().add(ingestion);
        ExternalRecord record = externalRecord(key("VOYAGE", "V-010"));
        externalRecords().add(record);
        ReceivedRecord quarantined = receivedRecord(record, ingestion, Classification.QUARANTINED, "前回と内容が違います");

        receivedRecords().add(quarantined);

        assertThat(storedReceivedRecord(quarantined.id())).contains(quarantined);
    }

    @Test
    void 行11_受信記録を取込と分類ごとに数える() {
        Ingestion ingestion = ingestion(3);
        Ingestion other = ingestion(1);
        ingestions().add(ingestion);
        ingestions().add(other);
        ExternalRecord record = externalRecord(key("VOYAGE", "V-011"));
        externalRecords().add(record);

        receivedRecords().add(receivedRecord(record, ingestion, Classification.ADOPTED, null));
        receivedRecords().add(receivedRecord(record, ingestion, Classification.DUPLICATE, null));
        receivedRecords().add(receivedRecord(record, ingestion, Classification.DUPLICATE, null));
        receivedRecords().add(receivedRecord(record, other, Classification.DUPLICATE, null));

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
