package com.example.cargotracker.identity.interfaces.web;

import com.example.cargotracker.identity.domain.model.aggregates.KpiObservation;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * KPI 計測記録の一覧の 1 行。輸送要求は内部の ID でなく業務番号で示す（D-4）。
 * 日時は UI 設計の共通部品「日時表示」（利用者のタイムゾーンを主にし、UTC を括弧で併記）で示す（BR-10。Bolt 21）。
 * アクセス・監査は見積りの画面の部品に依存できないため、同じ書式をここに置く（Bolt 21 の確認ポイント 7）。
 *
 * @param transportRequestNumber 業務番号の表記（Bolt 4 より前の記録では「業務番号なし」）
 * @param submittedAt 提出時刻（表示用）
 * @param firstPresentedAt 最初の提示時刻（表示用）。未提示なら null
 * @param leadTime KPI-01 リードタイム（表示用）。未提示なら null
 */
public record KpiObservationView(
        String transportRequestNumber, String submittedAt, String firstPresentedAt, String leadTime) {

    private static final String NO_NUMBER = "（業務番号なし）";

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");

    static KpiObservationView from(KpiObservation observation) {
        return new KpiObservationView(
                observation.transportRequestNumber() == null ? NO_NUMBER : observation.transportRequestNumber(),
                staffDateTime(observation.submittedAt()),
                observation
                        .firstPresentedAt()
                        .map(KpiObservationView::staffDateTime)
                        .orElse(null),
                observation.leadTime().map(KpiObservationView::formatDuration).orElse(null));
    }

    /** 社内の画面の日時（例: 2026-10-05 10:00 Asia/Tokyo（UTC+09:00）（UTC 2026-10-05 01:00））。 */
    static String staffDateTime(UtcInstant instant) {
        return DATE_TIME.format(ZonedDateTime.ofInstant(instant.instant(), DISPLAY_ZONE)) + "（UTC "
                + UTC_TIME.format(instant.instant().atOffset(ZoneOffset.UTC)) + "）";
    }

    /** KPI-01 リードタイムの表示。時間と分で示し、分未満は切り捨てる（Bolt 21 の確認ポイント 3）。 */
    static String formatDuration(Duration leadTime) {
        return leadTime.toHours() + " 時間 " + leadTime.toMinutesPart() + " 分";
    }
}
