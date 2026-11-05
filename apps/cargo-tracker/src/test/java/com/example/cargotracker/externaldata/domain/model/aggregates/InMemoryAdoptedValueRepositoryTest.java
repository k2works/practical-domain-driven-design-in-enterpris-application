package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.Instant;
import java.util.UUID;

/** メモリの実装で採用値のリポジトリの契約を確かめる（Bolt 39b3。この外部版の採用値は Bolt 39d3）。 */
class InMemoryAdoptedValueRepositoryTest extends AdoptedValueRepositoryContract {

    private static final Instant NOW = Instant.parse("2026-11-03T01:02:03Z");

    /** この外部版の採用値は受信記録の外部原本 ID で結ぶので、メモリの受信記録のリポジトリを渡す（Bolt 39d3）。 */
    private final InMemoryReceivedRecordRepository receivedRecords = new InMemoryReceivedRecordRepository(
            new InMemoryIngestionRepository(), new InMemoryExternalRecordRepository());

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
        // メモリの実装は受信記録から外部原本への外部キーを写さない
        return UUID.randomUUID();
    }

    @Override
    protected UUID givenReceivedRecordOf(UUID externalRecordId) {
        ReceivedRecord received = new ReceivedRecord(
                UUID.randomUUID(),
                externalRecordId,
                UUID.randomUUID(),
                PayloadHash.of(new byte[] {1}),
                PayloadObjectKey.newRecordPayload(),
                NOW.minusSeconds(60),
                Classification.ADOPTED,
                null,
                NOW,
                null,
                null);
        receivedRecords.add(received);
        return received.id();
    }
}
