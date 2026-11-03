package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ReceivedRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.Classification;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 受信記録のリポジトリの MyBatis 実装（Bolt 39b）。追記専用なので、追加と数えることだけを持つ。 */
@Repository
public class MyBatisReceivedRecordRepository implements ReceivedRecordRepository {

    private final ReceivedRecordMapper mapper;

    public MyBatisReceivedRecordRepository(ReceivedRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(ReceivedRecord receivedRecord) {
        mapper.insert(new ReceivedRecordRow(
                receivedRecord.id(),
                receivedRecord.externalRecordId(),
                receivedRecord.ingestionId(),
                receivedRecord.payloadHash().value(),
                receivedRecord.payloadObjectKey().value(),
                receivedRecord.acquiredAt().atOffset(ZoneOffset.UTC),
                receivedRecord.classification().name(),
                receivedRecord.classificationNote(),
                receivedRecord.classifiedAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public int countByIngestionAndClassification(UUID ingestionId, Classification classification) {
        return mapper.countByIngestionAndClassification(ingestionId, classification.name());
    }
}
