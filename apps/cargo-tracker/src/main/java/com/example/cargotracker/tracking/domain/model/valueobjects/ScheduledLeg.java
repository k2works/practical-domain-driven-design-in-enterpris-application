package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予定区間。予定のうち、1 つの航海で積地から揚地まで運ぶ部分（T-INV-12。Bolt 25）。値は経路設計の公開 API が返す、確定した経路版の
 * 区間の写し。区間番号は持たず、予定の区間の列の順で決まる。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定（積地）
 * @param arrivalAt 到着予定（揚地）
 */
@ValueObject
public record ScheduledLeg(
        String voyageNumber, Location load, Location discharge, UtcInstant departureAt, UtcInstant arrivalAt) {

    public ScheduledLeg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        if (!arrivalAt.instant().isAfter(departureAt.instant())) {
            throw new IllegalArgumentException("到着予定が出発予定より後ではありません: " + voyageNumber);
        }
    }
}
