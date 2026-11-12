package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PhysicalLineRange;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ReceivedValue;
import com.example.cargotracker.externaldata.domain.model.valueobjects.UndecidedRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * 受信記録のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加と数えることと、取込の隔離を読むこと（Bolt 39c3）と、判断を待つ隔離を
 * 読み数えること（Bolt 39d）と、ID で引くこと（Bolt 39d2）と、取込の判断を待つキーのない隔離を読み数えること（Bolt 39d2b）だけを持つ。
 * 受信記録の値（Bolt 39d3）は値の 2 列に書き、読むときは 2 列がそろって NULL なら値なしにする（表の CHECK が片方だけの NULL を拒む）。
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
        ReceivedValue value = receivedRecord.value();
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
                range == null ? null : range.last(),
                value == null ? null : value.targetKey(),
                value == null ? null : value.summary()));
    }

    @Override
    public int countByIngestionAndClassification(UUID ingestionId, Classification classification) {
        return mapper.countByIngestionAndClassification(ingestionId, classification.name());
    }

    @Override
    public List<ReceivedRecord> quarantinesAndDiscrepanciesOf(UUID ingestionId, int limit) {
        return mapper.quarantinesAndDiscrepanciesOf(ingestionId, limit).stream()
                .map(MyBatisReceivedRecordRepository::toDomain)
                .toList();
    }

    @Override
    public List<UndecidedRecord> undecidedRecords(UUID ingestionIdOrNull, int offset, int limit) {
        return mapper.undecidedRecords(ingestionIdOrNull, offset, limit).stream()
                .map(MyBatisReceivedRecordRepository::toDomain)
                .toList();
    }

    @Override
    public int countUndecidedRecords(UUID ingestionIdOrNull) {
        return mapper.countUndecidedRecords(ingestionIdOrNull);
    }

    @Override
    public Optional<ReceivedRecord> findById(UUID id) {
        return mapper.selectById(id).map(MyBatisReceivedRecordRepository::toDomain);
    }

    @Override
    public Optional<UndecidedRecord> undecidedRecord(UUID receivedRecordId) {
        return mapper.undecidedRecord(receivedRecordId, null).map(MyBatisReceivedRecordRepository::toDomain);
    }

    /** 取込 ID は必須（SQL の断片は取込 ID が NULL なら絞らないので、ここで拒む）。 */
    @Override
    public List<ReceivedRecord> undecidedKeylessQuarantinesOf(UUID ingestionId) {
        return mapper.undecidedKeylessQuarantinesOf(Objects.requireNonNull(ingestionId, "ingestionId")).stream()
                .map(MyBatisReceivedRecordRepository::toDomain)
                .toList();
    }

    @Override
    public int countUndecidedKeylessQuarantines(UUID ingestionId) {
        return mapper.countUndecidedKeylessQuarantines(Objects.requireNonNull(ingestionId, "ingestionId"));
    }

    private static UndecidedRecord toDomain(ReceivedRecordMapper.UndecidedRecordRow row) {
        return new UndecidedRecord(
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
                lineRangeOrNull(row.firstLineNumber(), row.lastLineNumber()),
                row.targetKey() == null ? null : new ReceivedValue(row.targetKey(), row.valueSummary()));
    }

    private static Instant instantOrNull(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
