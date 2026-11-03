package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecord;
import com.example.cargotracker.externaldata.domain.model.aggregates.ExternalRecordRepository;
import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 外部原本のリポジトリの MyBatis 実装（Bolt 39b）。冪等性キーの一意制約の違反は {@code DuplicateKeyException} のまま上げる
 * （確認ポイント 7）。
 */
@Repository
public class MyBatisExternalRecordRepository implements ExternalRecordRepository {

    private final ExternalRecordMapper mapper;

    public MyBatisExternalRecordRepository(ExternalRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
        return mapper.selectByKey(key.provider(), key.recordType(), key.externalId(), key.externalVersion())
                .map(MyBatisExternalRecordRepository::toDomain);
    }

    @Override
    public void add(ExternalRecord externalRecord) {
        IdempotencyKey key = externalRecord.key();
        mapper.insert(new ExternalRecordRow(
                externalRecord.id(),
                key.provider(),
                key.recordType(),
                key.externalId(),
                key.externalVersion(),
                externalRecord.payloadHash().value(),
                externalRecord.payloadObjectKey().value(),
                externalRecord.firstReceivedAt().atOffset(ZoneOffset.UTC)));
    }

    private static ExternalRecord toDomain(ExternalRecordRow row) {
        return new ExternalRecord(
                row.id(),
                new IdempotencyKey(row.provider(), row.recordType(), row.externalId(), row.externalVersion()),
                new PayloadHash(row.payloadHash()),
                new PayloadObjectKey(row.payloadObjectKey()),
                row.firstReceivedAt().toInstant());
    }
}
