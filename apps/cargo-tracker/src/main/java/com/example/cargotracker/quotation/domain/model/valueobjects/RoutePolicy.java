package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 経路方針。見積りとともに荷主へ示す、想定する主な経由地と概算の日程。詳細な経路は経路設計者の承認後に確定する。
 *
 * @param via 主な経由地（0〜5 件。空は経由地なし）
 * @param departureAt 概算の出発日時
 * @param arrivalAt 概算の到着日時（出発より後）
 */
@ValueObject
public record RoutePolicy(List<Location> via, UtcInstant departureAt, UtcInstant arrivalAt) {

    /** 主な経由地の数の上限。 */
    public static final int MAX_VIA = 5;

    public RoutePolicy {
        via = List.copyOf(via);
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        if (via.size() > MAX_VIA) {
            throw new IllegalArgumentException("主な経由地は " + MAX_VIA + " 件までです: " + via.size());
        }
        if (!arrivalAt.instant().isAfter(departureAt.instant())) {
            throw new IllegalArgumentException("概算の到着は出発より後です: " + departureAt + " → " + arrivalAt);
        }
    }
}
