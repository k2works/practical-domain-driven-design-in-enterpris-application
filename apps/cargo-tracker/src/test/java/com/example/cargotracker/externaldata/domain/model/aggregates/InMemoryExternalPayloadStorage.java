package com.example.cargotracker.externaldata.domain.model.aggregates;

import com.example.cargotracker.externaldata.domain.model.valueobjects.PayloadObjectKey;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** メモリ上の外部原本の保存（テスト用。Bolt 39）。保存と読み戻しでバイト列を写し、呼び手の配列の書き換えが原本に及ばないようにする。 */
public class InMemoryExternalPayloadStorage implements ExternalPayloadStorage {

    private final Map<PayloadObjectKey, byte[]> payloads = new ConcurrentHashMap<>();

    @Override
    public void store(PayloadObjectKey key, byte[] payload) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(payload, "payload");
        if (payloads.putIfAbsent(key, payload.clone()) != null) {
            throw new IllegalStateException("原本は上書きしない: " + key.value());
        }
    }

    @Override
    public byte[] read(PayloadObjectKey key) {
        byte[] payload = payloads.get(key);
        if (payload == null) {
            throw new NoSuchElementException("原本がない: " + key.value());
        }
        return payload.clone();
    }
}
