package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.IdempotencyKey;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadHash;
import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * メモリ上の外部原本のリポジトリ（テスト用。Bolt 39b）。冪等性キーの一意制約を写し、同じキーの 2 回目の追加は MyBatis の実装と同じく
 * {@link DuplicateExternalRecordException} にする（Bolt 39b3 の行 12）。
 */
public class InMemoryExternalRecordRepository implements ExternalRecordRepository {

    private final Map<IdempotencyKey, ExternalRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<ExternalRecord> findByKey(IdempotencyKey key) {
        return Optional.ofNullable(records.get(key));
    }

    @Override
    public Optional<ExternalRecord> findById(UUID externalRecordId) {
        return records.values().stream()
                .filter(externalRecord -> externalRecord.id().equals(externalRecordId))
                .findFirst();
    }

    @Override
    public void add(ExternalRecord externalRecord) {
        Objects.requireNonNull(externalRecord, "externalRecord");
        if (records.putIfAbsent(externalRecord.key(), externalRecord) != null) {
            throw DuplicateExternalRecordException.of(externalRecord.key(), null);
        }
    }

    @Override
    public void changeAdoptedPayload(
            UUID externalRecordId,
            PayloadHash expectedHash,
            PayloadHash adoptedHash,
            PayloadObjectKey adoptedObjectKey) {
        ExternalRecord current = records.values().stream()
                .filter(found -> found.id().equals(externalRecordId))
                .filter(found -> found.payloadHash().equals(expectedHash))
                .findFirst()
                .orElseThrow(() -> ConcurrentExternalRecordUpdateException.of(externalRecordId));
        records.put(
                current.key(),
                new ExternalRecord(
                        current.id(), current.key(), adoptedHash, adoptedObjectKey, current.firstReceivedAt()));
    }

    /** シナリオの開始時に消す（受入シナリオ。Bolt 39b）。 */
    public void clear() {
        records.clear();
    }

    /** 足した外部原本（ポートの外。テストの確かめ）。 */
    public List<ExternalRecord> all() {
        return List.copyOf(records.values());
    }
}
