package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 割り当てた区間。割り当てた経路の区間 1 つの写し（経路設計の区間。Bolt 20）。
 *
 * @param voyageNumber 航海番号
 * @param load 積地
 * @param discharge 揚地
 * @param departureAt 出発予定
 * @param arrivalAt 到着予定（出発より後）
 */
@ValueObject
public record AssignedRouteLeg(
        String voyageNumber, Location load, Location discharge, UtcInstant departureAt, UtcInstant arrivalAt) {

    public AssignedRouteLeg {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(discharge, "discharge");
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(arrivalAt, "arrivalAt");
        if (voyageNumber.isBlank()) {
            throw new IllegalArgumentException("航海番号がありません");
        }
        if (!arrivalAt.instant().isAfter(departureAt.instant())) {
            throw new IllegalArgumentException("区間の到着は出発より後です: " + departureAt + " → " + arrivalAt);
        }
    }

    /** 判定時刻にこの区間が出発済みか（出発予定が判定時刻と同時刻か前。D-88。Bolt 37e4）。 */
    public boolean isDepartedAt(UtcInstant at) {
        return isDepartedAt(departureAt, at);
    }

    /**
     * 出発予定が判定時刻と同時刻か前か（D-88。経路設計の確定の {@code ALREADY_DEPARTED} と同じ境界。見積りの中ではこの 1 か所で決め、
     * 読み取りモデル（予約の確定待ちの行）も使う。Bolt 37e4 の開発レビュー）。
     */
    public static boolean isDepartedAt(UtcInstant departureAt, UtcInstant at) {
        Objects.requireNonNull(departureAt, "departureAt");
        Objects.requireNonNull(at, "at");
        return !departureAt.instant().isAfter(at.instant());
    }
}
