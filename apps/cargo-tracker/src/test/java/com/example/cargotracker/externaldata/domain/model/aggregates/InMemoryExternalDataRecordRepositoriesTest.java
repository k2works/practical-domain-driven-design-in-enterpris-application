package com.example.cargotracker.externaldata.domain.model.aggregates;

import java.util.Optional;
import java.util.UUID;

/** メモリの実装で取込・外部原本・受信記録のリポジトリの契約を確かめる（Bolt 39b）。 */
class InMemoryExternalDataRecordRepositoriesTest extends ExternalDataRecordRepositoriesContract {

    private final InMemoryIngestionRepository ingestions = new InMemoryIngestionRepository();

    private final InMemoryExternalRecordRepository externalRecords = new InMemoryExternalRecordRepository();

    private final InMemoryReceivedRecordRepository receivedRecords =
            new InMemoryReceivedRecordRepository(ingestions, externalRecords);

    @Override
    protected IngestionRepository ingestions() {
        return ingestions;
    }

    @Override
    protected ExternalRecordRepository externalRecords() {
        return externalRecords;
    }

    @Override
    protected ReceivedRecordRepository receivedRecords() {
        return receivedRecords;
    }

    @Override
    protected void given(DataSource source) {
        // メモリの実装は取込から情報源への外部キーを写さない
    }

    @Override
    protected Optional<Ingestion> storedIngestion(UUID id) {
        return ingestions.all().stream()
                .filter(ingestion -> ingestion.id().equals(id))
                .findFirst();
    }

    @Override
    protected Optional<ReceivedRecord> storedReceivedRecord(UUID id) {
        return receivedRecords.all().stream()
                .filter(received -> received.id().equals(id))
                .findFirst();
    }
}
