package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedQuarantine;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 受信記録のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加と数えることと、取込の隔離を読むこと（Bolt 39c3）と、判断を待つ隔離を
 * 読み数えること（Bolt 39d）だけを持つ。
 */
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

    @Override
    public List<UndecidedQuarantine> undecidedQuarantines(UUID ingestionIdOrNull, int offset, int limit) {
        return mapper.undecidedQuarantines(ingestionIdOrNull, offset, limit).stream()
                .map(MyBatisReceivedRecordRepository::toDomain)
                .toList();
    }

    @Override
    public int countUndecidedQuarantines(UUID ingestionIdOrNull) {
        return mapper.countUndecidedQuarantines(ingestionIdOrNull);
    }

    private static UndecidedQuarantine toDomain(ReceivedRecordMapper.UndecidedQuarantineRow row) {
        return new UndecidedQuarantine(
                row.id(),
                row.ingestionId(),
                row.ingestedAt().toInstant(),
                row.dataSourceId(),
                lineRangeOrNull(row.firstLineNumber(), row.lastLineNumber()),
                row.externalId(),
                row.externalVersion(),
                row.classificationNote());
    }

    private static PhysicalLineRange lineRangeOrNull(Integer first, Integer last) {
        return first == null ? null : new PhysicalLineRange(first, last);
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
                lineRangeOrNull(row.firstLineNumber(), row.lastLineNumber()));
    }

    private static Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
