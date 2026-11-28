package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.Instant;
import java.util.UUID;

/** メモリの実装で採用値のリポジトリの契約を確かめる（Bolt 39b3。この外部版の採用値は Bolt 39d3、この外部 ID の採用値は Bolt 39h）。 */
class InMemoryAdoptedValueRepositoryTest extends AdoptedValueRepositoryContract {

    private static final Instant NOW = Instant.parse("2026-11-03T01:02:03Z");

    /** この外部 ID の採用値は外部原本の冪等性キーで絞るので、外部原本のリポジトリを受信記録のリポジトリと共有する（Bolt 39h）。 */
    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository();

    /** この外部版の採用値は受信記録の外部原本 ID で結ぶので、メモリの受信記録のリポジトリを渡す（Bolt 39d3）。 */
    private final InMemoryReceivedRecordRepository receivedRecords =
            new InMemoryReceivedRecordRepository(new InMemoryIngestionRepository(), externalRecords);

    private final InMemoryAdoptedValueRepository adoptedValues = new InMemoryAdoptedValueRepository(receivedRecords);

    @Override
    protected AdoptedValueRepository adoptedValues() {
        return adoptedValues;
    }

    @Override
    protected UUID givenReceivedRecord() {
        // メモリの実装は受信記録への外部キーを写さない
        return UUID.randomUUID();
    }

    @Override
    protected UUID givenExternalRecord(String externalId, String externalVersion) {
        return givenExternalRecordOf("採用値の提供元-" + UUID.randomUUID(), "VOYAGE", externalId, externalVersion);
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId) {
        return givenReceivedRecordOf(externalRecordId, NOW.minusSeconds(60), Classification.ADOPTED);
    }

    @Override
    protected UUID givenExternalRecordOf(
            String provider, String recordType, String externalId, String externalVersion) {
        ExternalRecord externalRecord = new ExternalRecord(
                UUID.randomUUID(),
                new IdempotencyKey(provider, recordType, externalId, externalVersion),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                NOW);
        externalRecords.add(externalRecord);
        return externalRecord.id();
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId, Instant acquiredAt, Classification classification) {
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecordId,
                UUID.randomUUID(),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                acquiredAt,
                classification,
                classification == Classification.ADOPTED ? null : "注記",
                NOW,
                null,
                null);
        receivedRecords.add(received);
        return received.id();
    }
}
