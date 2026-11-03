package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 採用値のリポジトリの MyBatis 実装（Bolt 39b3）。追記専用なので、追加と受信記録 ID での照会だけを持つ。 */
@Repository
public class MyBatisAdoptedValueRepository implements AdoptedValueRepository {

    private final AdoptedValueMapper mapper;

    public MyBatisAdoptedValueRepository(AdoptedValueMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(AdoptedValue adoptedValue) {
        mapper.insert(new AdoptedValueRow(
                adoptedValue.id(),
                adoptedValue.receivedRecordId(),
                adoptedValue.targetType().name(),
                adoptedValue.targetKey(),
                adoptedValue.valueSummary(),
                adoptedValue.infoVersion(),
                adoptedValue.publishedAt().atOffset(ZoneOffset.UTC)));
    }

    @Override
    public Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId) {
        return mapper.selectByReceivedRecordId(receivedRecordId).map(MyBatisAdoptedValueRepository::toDomain);
    }

    private static AdoptedValue toDomain(AdoptedValueRow row) {
        return new AdoptedValue(
                row.id(),
                row.receivedRecordId(),
                TargetType.valueOf(row.targetType()),
                row.targetKey(),
                row.valueSummary(),
                row.infoVersion(),
                row.publishedAt().toInstant());
    }
}
