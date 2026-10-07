package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 区間。1 つの航海で積地から揚地まで運ぶ部分。出発予定・到着予定は航海の寄港から取る。
 * 判定の根拠を示すため、区間を取った航海の採用情報版と取得時刻を写して持つ（R-INV-02。Bolt 17）。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定（積地）
 * @param arrivalAt 到着予定（揚地）
 * @param infoVersion 航海の採用情報版
 * @param infoAcquiredAt 航海の情報の取得時刻
 */
@ValueObject
public record Leg(
        String voyageNumber,
        Location load,
        Location discharge,
        UtcInstant departureAt,
        UtcInstant arrivalAt,
        String infoVersion,
        UtcInstant infoAcquiredAt) {

    public Leg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        Objects.requireNonNull(infoVersion, "infoVersion");
        Objects.requireNonNull(infoAcquiredAt, "infoAcquiredAt");
        if (!arrivalAt.instant().isAfter(departureAt.instant())) {
            throw new IllegalArgumentException("到着予定が出発予定より後ではありません: " + voyageNumber);
        }
    }
}
