package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * KPI 計測記録の一覧の 1 行。輸送要求は内部の ID でなく業務番号で示す（D-4）。時刻は UTC オフセットを付けて表示する（BR-10）。
 *
 * @param transportRequestNumber 業務番号の表記（Bolt 4 より前の記録では「業務番号なし」）
 * @param submittedAt 提出時刻（表示用）
 */
public record KpiObservationView(String transportRequestNumber, String submittedAt) {

    private static final String NO_NUMBER = "（業務番号なし）";

    private static final DateTimeFormatter UTC_WITH_OFFSET =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx").withZone(ZoneOffset.UTC);

    static KpiObservationView from(KpiObservation observation) {
        return new KpiObservationView(
                observation.transportRequestNumber() == null ? NO_NUMBER : observation.transportRequestNumber(),
                UTC_WITH_OFFSET.format(observation.submittedAt().instant()));
    }

    /** KPI-01 リードタイムの表示。骨組み（Bolt 21 ステップ 4 の Red）。 */
    static String leadTime(java.time.Duration leadTime) {
        return "";
    }
}
