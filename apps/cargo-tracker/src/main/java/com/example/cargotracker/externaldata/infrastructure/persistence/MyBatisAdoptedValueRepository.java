package com.example.cargotracker.externaldata.infrastructure.persistence;

import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValue.TargetType;
import com.example.cargotracker.externaldata.domain.model.aggregates.AdoptedValueRepository;
import com.example.cargotracker.externaldata.domain.model.aggregates.DuplicateAdoptedValueException;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 採用値のリポジトリの MyBatis 実装（Bolt 39b3）。追記専用なので、追加と受信記録 ID での照会と、この外部版の採用値の照会（Bolt 39d3）と、
 * この外部 ID の採用値の照会（Bolt 39h）だけを持つ。受信記録 ID の一意制約の違反
 * （{@link DuplicateKeyException}）は、元の例外を原因に持つ {@link DuplicateAdoptedValueException} に写して上げる（行 15）。
 */
@Repository
public class MyBatisAdoptedValueRepository implements AdoptedValueRepository {

    private final AdoptedValueMapper mapper;

    public MyBatisAdoptedValueRepository(AdoptedValueMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void add(AdoptedValue adoptedValue) {
        try {
            mapper.insert(new AdoptedValueRow(
                    adoptedValue.id(),
                    adoptedValue.receivedRecordId(),
                    adoptedValue.targetType().name(),
                    adoptedValue.targetKey(),
                    adoptedValue.valueSummary(),
                    adoptedValue.infoVersion(),
                    adoptedValue.publishedAt().atOffset(ZoneOffset.UTC)));
        } catch (DuplicateKeyException e) {
            throw DuplicateAdoptedValueException.of(adoptedValue.receivedRecordId(), e);
        }
    }

    @Override
    public Optional<AdoptedValue> findByReceivedRecordId(UUID receivedRecordId) {
        return mapper.selectByReceivedRecordId(receivedRecordId).map(MyBatisAdoptedValueRepository::toDomain);
    }

    @Override
    public Optional<AdoptedValue> currentOf(UUID externalRecordId) {
        return mapper.selectCurrentOf(externalRecordId).map(MyBatisAdoptedValueRepository::toDomain);
    }

    @Override
    public List<ExternalIdAdoptedValue> findByExternalId(String provider, String recordType, String externalId) {
        return mapper.selectByExternalId(provider, recordType, externalId).stream()
                .map(row -> new ExternalIdAdoptedValue(
                        row.infoVersion(),
                        row.targetKey(),
                        row.acquiredAt().toInstant(),
                        row.publishedAt().toInstant(),
                        row.receivedRecordId()))
                .toList();
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
