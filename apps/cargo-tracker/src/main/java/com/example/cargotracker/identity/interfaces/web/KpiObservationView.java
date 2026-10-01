package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * KPI 計測記録の一覧の 1 行。時刻は UTC オフセットを付けて表示する（BR-10）。
 *
 * @param transportRequestId 輸送要求 ID
 * @param submittedAt 提出時刻（表示用）
 */
public record KpiObservationView(UUID transportRequestId, String submittedAt) {

    private static final DateTimeFormatter UTC_WITH_OFFSET = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx")
            .withZone(ZoneOffset.UTC);

    static KpiObservationView from(KpiObservation observation) {
        return new KpiObservationView(observation.transportRequestId(),
                UTC_WITH_OFFSET.format(observation.submittedAt().instant()));
    }
}
