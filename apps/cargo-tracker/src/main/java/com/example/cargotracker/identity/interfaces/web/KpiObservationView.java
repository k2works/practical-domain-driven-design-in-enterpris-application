package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.platform.web.DurationDisplay;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;

/**
 * KPI 計測記録の一覧の 1 行。輸送要求は内部の ID でなく業務番号で示す（D-4）。
 * 日時は UI 設計の共通部品「日時表示」（利用者のタイムゾーンを主にし、UTC を括弧で併記）で示す（BR-10。Bolt 21）。
 * 日時表示・期間表示は platform の Web の部品を使う（Bolt 22、#41）。
 *
 * @param transportRequestNumber 業務番号の表記（Bolt 4 より前の記録では「業務番号なし」）
 * @param submittedAt 提出時刻（表示用）
 * @param firstPresentedAt 最初の提示時刻（表示用）。未提示なら null
 * @param leadTime KPI-01 リードタイム（表示用）。未提示なら null
 * @param elapsedSinceSubmission 未提示の行の、一覧を開いた時刻での提出からの経過時間（表示用）。提示済みなら null
 */
public record KpiObservationView(
        String transportRequestNumber,
        String submittedAt,
        String firstPresentedAt,
        String leadTime,
        String elapsedSinceSubmission) {

    private static final String NO_NUMBER = "（業務番号なし）";

    static KpiObservationView from(KpiObservation observation, Instant now) {
        return new KpiObservationView(
                observation.transportRequestNumber() == null ? NO_NUMBER : observation.transportRequestNumber(),
                staffDateTime(observation.submittedAt()),
                observation
                        .firstPresentedAt()
                        .map(KpiObservationView::staffDateTime)
                        .orElse(null),
                observation.leadTime().map(DurationDisplay::hoursAndMinutes).orElse(null),
                observation.firstPresentedAt().isPresent()
                        ? null
                        : DurationDisplay.hoursAndMinutes(
                                Duration.between(observation.submittedAt().instant(), now)));
    }

    private static String staffDateTime(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
