package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 受信記録のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加と数えることと、取込の隔離を読むこと（Bolt 39c3）だけを持つ。 */
@Repository
public class MyBatisReceivedRecordRepository implements ReceivedRecordRepository {

    private final ReceivedRecordMapper mapper;

    public MyBatisReceivedRecordRepository(ReceivedRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(ReceivedRecord receivedRecord) {
        PhysicalLineRange range = receivedRecord.lineRange();
        mapper.insert(new ReceivedRecordRow(
                receivedRecord.id(),
                receivedRecord.externalRecordId(),
                receivedRecord.ingestionId(),
                receivedRecord.payloadHash().value(),
                receivedRecord.payloadObjectKey().value(),
                receivedRecord.acquiredAt() == null
                        ? null
                        : receivedRecord.acquiredAt().atOffset(ZoneOffset.UTC),
                receivedRecord.classification().name(),
                receivedRecord.classificationNote(),
                receivedRecord.classifiedAt().atOffset(ZoneOffset.UTC),
                range == null ? null : range.first(),
                range == null ? null : range.last()));
    }

    @Override
    public int countByIngestionAndClassification(UUID ingestionId, Classification classification) {
        return mapper.countByIngestionAndClassification(ingestionId, classification.name());
    }

    @Override
    public List<ReceivedRecord> quarantinedOf(UUID ingestionId, int limit) {
        return mapper.quarantinedOf(ingestionId, limit).stream()
                .map(MyBatisReceivedRecordRepository::toDomain)
                .toList();
    }

    private static ReceivedRecord toDomain(ReceivedRecordRow row) {
        return new ReceivedRecord(
                row.id(),
                row.externalRecordId(),
                row.ingestionId(),
                new PayloadHash(row.payloadHash()),
                new PayloadObjectKey(row.payloadObjectKey()),
                instantOrNull(row.acquiredAt()),
                Classification.valueOf(row.classification()),
                row.classificationNote(),
                row.classifiedAt().toInstant(),
                row.firstLineNumber() == null
                        ? null
                        : new PhysicalLineRange(row.firstLineNumber(), row.lastLineNumber()));
    }

    private static Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
