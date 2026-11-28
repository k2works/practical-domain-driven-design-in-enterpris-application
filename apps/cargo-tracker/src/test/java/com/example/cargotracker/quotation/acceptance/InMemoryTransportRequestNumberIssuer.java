package com.example.cargotracker.quotation.acceptance;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestNumberIssuer;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオと単体テストで使う、メモリ上の業務番号の採番。
 * 本物（年ごとのカウンター表）と同じく、年ごとに 1 から数える。
 */
public class InMemoryTransportRequestNumberIssuer implements TransportRequestNumberIssuer {

    private final Map<Integer, Integer> lastNumbers = new ConcurrentHashMap<>();

    @Override
    public TransportRequestNumber next(int year) {
        return new TransportRequestNumber(year, lastNumbers.merge(year, 1, Integer::sum));
    }

    /** 振った番号の数。提出を受け付けなかったときに採番していないことを確かめる。 */
    public int issuedCount() {
        return lastNumbers.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** シナリオの開始時に採番を初期化する。 */
    public void clear() {
        lastNumbers.clear();
    }
}
