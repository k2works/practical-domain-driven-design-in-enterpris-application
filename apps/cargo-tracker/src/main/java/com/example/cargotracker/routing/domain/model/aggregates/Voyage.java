package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 航海。航海番号で識別される運航予定。外部原本から採用した版を持つ（集約ルート）。
 * 外部原本の取込（US-14、W7）ができるまでは、開発環境の仮の航海データだけを読む（Bolt 17）。寄港は運航の順に並ぶ。
 *
 * @param voyageNumber 航海番号
 * @param portCalls 寄港（運航の順。2 つ以上）
 * @param adoptedInfoVersion 採用情報版
 * @param acquiredAt 情報の取得時刻
 */
@AggregateRoot
public record Voyage(String voyageNumber, List<PortCall> portCalls, String adoptedInfoVersion, UtcInstant acquiredAt) {

    public Voyage {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(adoptedInfoVersion, "adoptedInfoVersion");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        portCalls = List.copyOf(portCalls);
        if (portCalls.size() < 2) {
            throw new IllegalArgumentException("航海には 2 つ以上の寄港が要ります: " + voyageNumber);
        }
    }
}
