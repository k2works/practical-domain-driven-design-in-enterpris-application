package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** S-11・S-12 の表示の値（社内の画面の日時は利用者のタイムゾーンと UTC を併記する。Bolt 22・26）。 */
final class TrackingRecordViews {

    /**
     * 追跡状態の表示名（ドメインモデルの追跡状態の候補の名前。業務責任者の確認前。Bolt 26 計画の確認ポイント 6）。荷主の照会（US-09）も
     * 同じ名前を使う。10 個の値を表に置く（switch では循環的複雑度の上限を超えるため）。すべての値があることは画面の単体テストで確かめる。
     */
    private static final Map<TrackingStatus, String> STATUS_NAMES = new EnumMap<>(Map.of(
            TrackingStatus.BOOKED, "予約確定",
            TrackingStatus.PICKUP_SCHEDULED, "集荷予定",
            TrackingStatus.PICKED_UP, "集荷済み",
            TrackingStatus.RECEIVED_AT_ORIGIN, "出発地搬入済み",
            TrackingStatus.IN_TRANSIT, "輸送中",
            TrackingStatus.TRANSSHIPPING, "積替え中",
            TrackingStatus.ARRIVED_AT_DESTINATION, "目的地到着",
            TrackingStatus.READY_FOR_DELIVERY, "引渡し可能",
            TrackingStatus.DELIVERED, "引渡し済み",
            TrackingStatus.UNDER_REVIEW, "確認中"));

    private TrackingRecordViews() {}

    static Optional<TrackingNumber> parseTrackingNumber(String text) {
        try {
            return Optional.of(new TrackingNumber(text));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    static String status(TrackingStatus status) {
        return STATUS_NAMES.get(status);
    }

    /**
     * S-11 の 1 行。
     *
     * @param trackingNumber 追跡番号
     * @param status 現在状態の表示名
     * @param originalEta 当初の到着予定
     * @param startedAt 追跡の開始時刻
     */
    record Row(String trackingNumber, String status, String originalEta, String startedAt) {}

    static List<Row> list(RecentTrackingRecords recent) {
        return recent.rows().stream()
                .map(summary -> new Row(
                        summary.trackingNumber().value(),
                        status(summary.currentStatus()),
                        staff(summary.originalEta()),
                        staff(summary.startedAt())))
                .toList();
    }

    /**
     * S-12 の表示。
     *
     * @param trackingNumber 追跡番号
     * @param status 現在状態の表示名
     * @param originalEta 当初の到着予定
     * @param latestEta 最新の到着見込み
     * @param route 予定の経路版（案件番号と経路版）
     * @param legs 予定区間（区間の順）
     */
    record DetailView(
            String trackingNumber,
            String status,
            String originalEta,
            String latestEta,
            String route,
            List<LegView> legs) {}

    /**
     * 予定区間の 1 項目。
     *
     * @param summary 区間・航海・積地 → 揚地
     * @param departureAt 出発予定
     * @param arrivalAt 到着予定
     */
    record LegView(String summary, String departureAt, String arrivalAt) {}

    static DetailView detail(TrackingRecord trackingRecord) {
        Schedule schedule = trackingRecord.schedule();
        List<ScheduledLeg> legs = schedule.legs();
        return new DetailView(
                trackingRecord.trackingNumber().value(),
                status(trackingRecord.currentStatus()),
                staff(trackingRecord.originalEta()),
                staff(trackingRecord.latestEta()),
                schedule.routingCaseNumber() + " 版 " + schedule.routeVersionNo(),
                IntStream.range(0, legs.size())
                        .mapToObj(i -> leg(i + 1, legs.get(i)))
                        .toList());
    }

    private static LegView leg(int legNo, ScheduledLeg leg) {
        return new LegView(
                "区間 " + legNo + "　航海 " + leg.voyageNumber() + "　" + leg.load().unLocode() + " → "
                        + leg.discharge().unLocode(),
                staff(leg.departureAt()),
                staff(leg.arrivalAt()));
    }

    private static String staff(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
