package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** S-11・S-12・S-13 の表示の値（社内の画面の日時は利用者のタイムゾーンと UTC を併記する。Bolt 22・26・26c）。 */
final class TrackingRecordViews {

    /**
     * 追跡状態の表示名（ドメインモデルの追跡状態の候補の名前。業務責任者の確認前。Bolt 26 計画の確認ポイント 6）。荷主の照会（US-09）も
     * 同じ名前を使う。10 個の値を表に置く（switch では循環的複雑度の上限を超えるため）。表にない値は例外にし、すべての値に表示名があることを画面の単体テストで
     * 確かめる（値を足したときの抜けを拾う。Bolt 26 レビュー P-1）。
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

    /** 実績の種類の表示名（用語集の実績の種類。Bolt 26c）。 */
    private static final Map<MilestoneKind, String> KIND_NAMES = new EnumMap<>(Map.of(
            MilestoneKind.PICKUP, "集荷",
            MilestoneKind.RECEIPT_AT_ORIGIN, "搬入",
            MilestoneKind.DEPARTURE, "出発",
            MilestoneKind.TRANSSHIPMENT, "積替",
            MilestoneKind.ARRIVAL, "到着",
            MilestoneKind.DELIVERY, "引渡し"));

    /** 出典の種類の表示名（用語集の出典の種類。Bolt 26c）。 */
    private static final Map<SourceKind, String> SOURCE_KIND_NAMES = new EnumMap<>(Map.of(
            SourceKind.EXTERNAL_RECORD, "外部原本",
            SourceKind.FIELD_RECORD, "現場記録",
            SourceKind.INTERNAL_CHECK, "社内確認",
            SourceKind.MANUAL_ENTRY, "手動入力"));

    /** 実績の状態の表示名（用語集の実績の状態。Bolt 26c）。 */
    private static final Map<MilestoneState, String> MILESTONE_STATE_NAMES = new EnumMap<>(Map.of(
            MilestoneState.DRAFT, "下書き",
            MilestoneState.ADOPTED, "採用",
            MilestoneState.UNDER_REVIEW, "確認中",
            MilestoneState.RETAINED_ONLY, "保持のみ"));

    private TrackingRecordViews() {}

    static Optional<TrackingNumber> parseTrackingNumber(String text) {
        try {
            return Optional.of(new TrackingNumber(text));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    static String status(TrackingStatus status) {
        return name(STATUS_NAMES, status, "追跡状態");
    }

    static String kind(MilestoneKind kind) {
        return name(KIND_NAMES, kind, "実績の種類");
    }

    static String sourceKind(SourceKind sourceKind) {
        return name(SOURCE_KIND_NAMES, sourceKind, "出典の種類");
    }

    static String milestoneState(MilestoneState state) {
        return name(MILESTONE_STATE_NAMES, state, "実績の状態");
    }

    /** 表にない値は例外にする（値を足したときの抜けを画面の単体テストで拾う）。 */
    private static <E extends Enum<E>> String name(Map<E, String> names, E value, String what) {
        String name = names.get(value);
        if (name == null) {
            throw new IllegalStateException(what + "の表示名がありません: " + value);
        }
        return name;
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
     * @param milestones 主要実績（発生時刻の順。同じ時刻は実績番号の順。Bolt 26c）
     */
    record DetailView(
            String trackingNumber,
            String status,
            String originalEta,
            String latestEta,
            String route,
            List<LegView> legs,
            List<MilestoneView> milestones) {}

    /**
     * 予定区間の 1 項目。
     *
     * @param summary 区間・航海・積地から揚地
     * @param departureAt 出発予定
     * @param arrivalAt 到着予定
     */
    record LegView(String summary, String departureAt, String arrivalAt) {}

    /**
     * 主要実績の 1 項目（Bolt 26c）。
     *
     * @param anchor 項目の id（既存の実績へのリンクの行き先。{@code milestone-1}）
     * @param summary 実績番号・種類・場所
     * @param occurredAt 発生時刻
     * @param source 出典（種類・参照・取得時刻）
     * @param state 状態の表示名
     */
    record MilestoneView(String anchor, String summary, String occurredAt, String source, String state) {}

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
                        .toList(),
                trackingRecord.milestones().stream()
                        .sorted(Comparator.comparing((Milestone milestone) ->
                                        milestone.occurredAt().instant())
                                .thenComparingInt(Milestone::milestoneNo))
                        .map(TrackingRecordViews::milestone)
                        .toList());
    }

    /** 区間の表記は「→」でなく「から」にする（スクリーンリーダーが「右矢印」と読むため。Bolt 26 の U-4。Bolt 26c）。 */
    private static LegView leg(int legNo, ScheduledLeg leg) {
        return new LegView(
                "区間 " + legNo + "、航海 " + leg.voyageNumber() + "、" + leg.load().unLocode() + " から "
                        + leg.discharge().unLocode(),
                staff(leg.departureAt()),
                staff(leg.arrivalAt()));
    }

    private static MilestoneView milestone(Milestone milestone) {
        return new MilestoneView(
                "milestone-" + milestone.milestoneNo(),
                "実績 " + milestone.milestoneNo() + "、" + kind(milestone.kind()) + "、"
                        + milestone.location().unLocode(),
                staff(milestone.occurredAt()),
                source(milestone),
                milestoneState(milestone.state()));
    }

    /** 出典の表記（「現場記録 F-118（取得 …）」）。結果の文言にも使う。 */
    static String sourceLabel(SourceKind sourceKind, String reference) {
        return sourceKind(sourceKind) + " " + reference;
    }

    private static String source(Milestone milestone) {
        return sourceLabel(milestone.source().kind(), milestone.source().reference()) + "（取得 "
                + staff(milestone.source().acquiredAt()) + "）";
    }

    private static String staff(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
