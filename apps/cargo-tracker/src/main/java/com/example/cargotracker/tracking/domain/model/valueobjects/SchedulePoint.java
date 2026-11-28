package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予定の時点。予定区間の出発予定・到着予定に、実績の種類と場所と区間の位置を添えたもの（Bolt 39g4。開始準備の決定 (8)）。予定の順に区間 1 の
 * 出発、区間 1 の積替（最後の区間なら到着）、区間 2 の出発…と並ぶ（{@link Schedule#points()}）。採用の実績はここに結ぶ（最新の到着見込みの規則と
 * 次の予定の実績の規則が同じ結び方を使う）。
 *
 * @param kind 実績の種類（出発・積替・到着）
 * @param location 場所（出発は積地、積替と到着は揚地）
 * @param scheduledAt 予定時刻（出発は出発予定、積替と到着は到着予定）
 * @param legIndex 区間の位置（0 から。区間番号 - 1）
 */
@ValueObject
public record SchedulePoint(MilestoneKind kind, Location location, UtcInstant scheduledAt, int legIndex) {

    public SchedulePoint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        if (legIndex < 0) {
            throw new IllegalArgumentException("区間の位置は 0 から: " + legIndex);
        }
    }
}
