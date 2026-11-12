package com.example.cargotracker.tracking.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.queryservices.RecentTrackingRecords;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordDetail;
import com.example.cargotracker.tracking.domain.model.aggregates.MilestoneAdoption;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.AdoptionRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerMilestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.CustomerTrackingView;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.SchedulePoint;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
     * @param latestEta 最新の到着見込み（Bolt 39g3。U-3）
     * @param lastAcquiredAt 最終取得時刻（なければ「なし」。Bolt 39g3）
     * @param startedAt 追跡の開始時刻
     * @param underReview 現在状態が確認中か（状態バッジで示す。Bolt 39gc）
     * @param freshnessExceeded 照会したときに鮮度超過か（現在状態のセルに鮮度超過の印を並べる。Bolt 39g4）
     */
    record Row(
            String trackingNumber,
            String status,
            String originalEta,
            String latestEta,
            String lastAcquiredAt,
            String startedAt,
            boolean underReview,
            boolean freshnessExceeded) {}

    /** S-11 の最終取得時刻のないセルの文（本計画 CP-5。Bolt 39g3）。 */
    static final String NO_LAST_ACQUIRED_IN_LIST = "なし";

    /** S-12・C-10 の照会の結果の最終取得時刻がないときの文（本計画 CP-5。Bolt 39g3）。 */
    static final String NO_LAST_ACQUIRED_YET = "まだありません";

    static List<Row> list(RecentTrackingRecords recent) {
        return recent.rows().stream()
                .map(summary -> new Row(
                        summary.trackingNumber().value(),
                        status(summary.currentStatus()),
                        staff(summary.originalEta()),
                        staff(summary.latestEta()),
                        summary.lastAcquiredAt().map(TrackingRecordViews::staff).orElse(NO_LAST_ACQUIRED_IN_LIST),
                        staff(summary.startedAt()),
                        summary.currentStatus() == TrackingStatus.UNDER_REVIEW,
                        recent.freshnessExceeded(summary)))
                .toList();
    }

    /** S-12 の次の予定の実績がないときの文（本計画 CP-4。Bolt 39g4）。 */
    static final String NO_NEXT_EXPECTED = "なし";

    /**
     * S-12 の鮮度超過の警告の文（本計画 CP-4、D-135。Bolt 39g4）。時間は判定の許容範囲から作る（直書きしない。前例 Bolt 33c のロックの方針の値）。
     *
     * @param tolerance 鮮度の判定の許容範囲
     */
    static String freshnessWarning(Duration tolerance) {
        return "次の予定の実績が予定時刻から " + tolerance.toHours() + " 時間を過ぎても届いていません。";
    }

    /**
     * S-12 の表示。
     *
     * @param trackingNumber 追跡番号
     * @param status 現在状態の表示名
     * @param originalEta 当初の到着予定
     * @param latestEta 最新の到着見込み
     * @param latestEstimateBasis 見込みの根拠の文（根拠がなければ「記録がありません」。Bolt 39g4c）
     * @param lastAcquiredAt 最終取得時刻（なければ「まだありません」。Bolt 39g3）
     * @param nextExpectedAt 次の予定の実績の予定時刻（なければ「なし」。Bolt 39g4）
     * @param freshnessWarning 鮮度超過の警告の文（鮮度超過でなければ {@code null}。Bolt 39g4）
     * @param route 予定の経路版（案件番号と経路版）
     * @param legs 予定区間（区間の順）
     * @param milestones 主要実績（発生時刻の順。同じ時刻は実績番号の順。Bolt 26c）
     * @param version 追跡記録の版（下書きの採用の隠し項目。競合の検出。Bolt 39gb）
     * @param underReview 現在状態が確認中か（状態バッジで示す。Bolt 39gc）
     * @param underReviewNote 確認中の理由の文（確認中でなければ {@code null}。Bolt 39gc）
     */
    @SuppressWarnings("java:S107") // 画面の 1 つの表示の値をそのまま持つ
    record DetailView(
            String trackingNumber,
            String status,
            String originalEta,
            String latestEta,
            String latestEstimateBasis,
            String lastAcquiredAt,
            String nextExpectedAt,
            String freshnessWarning,
            String route,
            List<LegView> legs,
            List<MilestoneView> milestones,
            long version,
            boolean underReview,
            String underReviewNote) {}

    /**
     * 予定区間の 1 項目。
     *
     * @param summary 航海・積地から揚地（社内の S-12 は先頭に「区間 n、」を付ける。荷主の C-10 は付けない。Bolt 37f3）
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
     * @param milestoneNo 実績番号（採用の送り先）
     * @param adoptable 採用のボタンを出すか（候補で採用できる下書き。Bolt 39gb）
     * @param adoptionNote 採用の欄の文（置き換えられた下書き・採用できない理由。なければ {@code null}。Bolt 39gb）
     */
    record MilestoneView(
            String anchor,
            String summary,
            String occurredAt,
            String source,
            String state,
            int milestoneNo,
            boolean adoptable,
            String adoptionNote) {}

    /** 置き換えられた下書きの文（Bolt 39gb の行 43。行 10・11 で「新しい原本」は偽になるのでこの文にする）。 */
    static final String SUPERSEDED = "後から公開された原本に置き換えられました";

    /** 採用済みと同じ値の候補の文（Bolt 39gb の行 53）。 */
    static final String SAME_AS_ADOPTED = "採用済みの実績と同じ値です";

    /** 確認中の実績と同じ値の候補の文（Bolt 39gc の行 14）。 */
    static final String SAME_AS_UNDER_REVIEW = "確認中の実績と同じ値です";

    /** 確認中の実績と値が違う候補の文（Bolt 39gc の行 13）。 */
    static final String CONFLICTS_WITH_UNDER_REVIEW = "確認中の実績と値が違います";

    /** 採用すると確認中になる候補の注記（Bolt 39gc の行 15。本計画 CP-7）。 */
    static final String ADOPTION_PLACES_UNDER_REVIEW = "採用すると、時系列に反するため確認中になります";

    /**
     * 採用の拒否の理由の文（Bolt 39gb。画面の欄と POST の結果で同じ文を使う）。判断済みの実績が確認中なら、値の違いの文を確認中の文にする（Bolt 39gc）。
     *
     * @param reason 理由
     * @param decidedState 値の違う判断済みの実績の状態（なければ空）
     */
    static String adoptionRejection(AdoptionRejectionReason reason, Optional<MilestoneState> decidedState) {
        if (reason == AdoptionRejectionReason.CONFLICTS_WITH_ADOPTED
                && decidedState.filter(MilestoneState.UNDER_REVIEW::equals).isPresent()) {
            return CONFLICTS_WITH_UNDER_REVIEW;
        }
        return adoptionRejection(reason);
    }

    /** 採用の拒否の理由の文（Bolt 39gb。画面の欄と POST の結果で同じ文を使う）。 */
    static String adoptionRejection(AdoptionRejectionReason reason) {
        return switch (reason) {
            case NOT_CANDIDATE -> "候補ではありません";
            case CONFLICTS_WITH_ADOPTED -> "採用済みの実績と値が違います";
            case BOOKING_CANCELLED -> "取消済みの予約の実績は採用できません";
            case TRACKING_COMPLETED -> "完了した追跡の実績は採用できません";
            case OCCURRED_IN_FUTURE -> "発生時刻がいまより後の実績は採用できません";
        };
    }

    /**
     * S-12 の表示。下書きの採用の欄は、閲覧者が今採用したらどうなるかを同じドメインの操作（{@link TrackingRecord#adoptCandidate}。純粋）で試して決める
     * （ボタンと POST の判定をずらさない。Bolt 39gb）。
     *
     * @param found 追跡記録と照会したときの鮮度の判定（Bolt 39g4。判定は照会サービスが行い、ここでは示すだけ）
     * @param viewer 閲覧者（採用者になる追跡管理者）
     * @param now 今の時刻（未来の発生時刻の判定）
     */
    static DetailView detail(TrackingRecordDetail found, UserId viewer, UtcInstant now) {
        TrackingRecord trackingRecord = found.trackingRecord();
        Schedule schedule = trackingRecord.schedule();
        return new DetailView(
                trackingRecord.trackingNumber().value(),
                status(trackingRecord.currentStatus()),
                staff(trackingRecord.originalEta()),
                staff(trackingRecord.latestEta()),
                latestEstimateBasis(trackingRecord),
                trackingRecord.lastAcquiredAt().map(TrackingRecordViews::staff).orElse(NO_LAST_ACQUIRED_YET),
                trackingRecord.nextExpectedAt().map(TrackingRecordViews::staff).orElse(NO_NEXT_EXPECTED),
                found.freshnessExceeded() ? freshnessWarning(found.freshnessTolerance()) : null,
                schedule.routingCaseNumber() + " 版 " + schedule.routeVersionNo(),
                legs(schedule.legs(), TrackingRecordViews::staff, TrackingRecordViews::legSummary),
                trackingRecord.milestones().stream()
                        .sorted(Comparator.comparing((Milestone milestone) ->
                                        milestone.occurredAt().instant())
                                .thenComparingInt(Milestone::milestoneNo))
                        .map(milestone -> milestone(trackingRecord, milestone, viewer, now))
                        .toList(),
                trackingRecord.aggregateVersion(),
                trackingRecord.currentStatus() == TrackingStatus.UNDER_REVIEW,
                underReviewNote(trackingRecord));
    }

    /** S-12 の見込みの根拠のない行（Bolt 39g4c の前に見込みを決めた追跡記録）の文（【業務の判断】(ax)、台帳 D-140）。 */
    static final String NO_LATEST_ESTIMATE_BASIS = "記録がありません";

    /**
     * S-12 の見込みの根拠の文（Bolt 39g4c の行 6。【業務の判断】(aw)・(ax)、計画 決定 (5)）。区間は根拠の実績を結んだ予定の時点、時間は集約の見込みの遅れ
     * （最新の到着見込み − 当初の到着予定）を書式にするだけで、規則の加算の形を写さない。航海の到着予定は最後の区間の航海番号と取得時刻。
     */
    private static String latestEstimateBasis(TrackingRecord trackingRecord) {
        return trackingRecord
                .latestEstimateBasis()
                .map(basis -> switch (basis.kind()) {
                    case ORIGINAL_ETA -> "当初の到着予定";
                    case MILESTONE ->
                        milestoneBasis(trackingRecord, basis.milestoneNo().getAsInt());
                    case VOYAGE_ARRIVAL ->
                        "航海 " + trackingRecord.schedule().legs().getLast().voyageNumber() + " の到着予定（取得 "
                                + staff(trackingRecord
                                        .voyageArrival()
                                        .orElseThrow()
                                        .order()
                                        .acquiredAt())
                                + "）";
                })
                .orElse(NO_LATEST_ESTIMATE_BASIS);
    }

    private static String milestoneBasis(TrackingRecord trackingRecord, int milestoneNo) {
        Milestone milestone = trackingRecord.milestones().get(milestoneNo - 1);
        String place = "実績 " + milestoneNo + "、" + milestone.location().unLocode();
        if (milestone.kind() == MilestoneKind.ARRIVAL) {
            return "到着の実績（" + place + "）";
        }
        SchedulePoint point = trackingRecord.latestEstimateBasisPoint().orElseThrow();
        return "区間 " + (point.legIndex() + 1) + " の" + kind(milestone.kind()) + "の実績（" + place + "、"
                + delayFromOriginal(trackingRecord.latestEstimateDelay()) + "）";
    }

    /** 当初からの見込みの遅れの書式（時間と分。分未満は切り捨て。0 分は「当初の到着予定どおり」）。 */
    private static String delayFromOriginal(Duration delay) {
        long minutes = delay.toMinutes();
        if (minutes <= 0) {
            return "当初の到着予定どおり";
        }
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return "当初より " + rest + " 分遅れ";
        }
        String minutesPart = rest == 0 ? "" : " " + rest + " 分";
        return "当初より " + hours + " 時間" + minutesPart + "遅れ";
    }

    /**
     * 確認中の理由の文（Bolt 39gc の行 23。決定 (8)）。何が分かっていて何が分かっていないかを分けて示す（ui_design の原則）: 時系列に反する実績の番号と、
     * 採用済みの実績から導いた状態。確認中でなければ {@code null}。
     */
    private static String underReviewNote(TrackingRecord trackingRecord) {
        if (trackingRecord.underReviewReason().isEmpty()) {
            return null;
        }
        return "時系列に反する実績があります（実績 " + trackingRecord.statusBasisMilestoneNo().orElseThrow() + "）。採用済みの実績から導いた状態は "
                + status(trackingRecord.statusFromAdopted()) + " です。";
    }

    /**
     * 予定区間の項目（S-12 と C-10 で形は同じ。日時の書式と 1 行の表記が社内と荷主で違う。Bolt 27・37f3）。積地と揚地は「→」でなく
     * 「から」でつなぐ（スクリーンリーダーが「右矢印」と読むため。Bolt 26 の U-4。Bolt 26c）。
     */
    private static List<LegView> legs(
            List<ScheduledLeg> legs,
            Function<UtcInstant, String> dateTime,
            BiFunction<Integer, ScheduledLeg, String> summary) {
        return IntStream.range(0, legs.size())
                .mapToObj(i -> new LegView(
                        summary.apply(i + 1, legs.get(i)),
                        dateTime.apply(legs.get(i).departureAt()),
                        dateTime.apply(legs.get(i).arrivalAt())))
                .toList();
    }

    /** 社内の S-12 の 1 行（「区間 n、航海 …」。区間は社内の語）。 */
    private static String legSummary(int legNo, ScheduledLeg leg) {
        return "区間 " + legNo + "、" + customerLegSummary(leg);
    }

    /**
     * 荷主の C-10 の 1 行（「航海 …、積地 から 揚地」）。社内の語「区間」と番号は出さず、順は順序付きのリストの並びで示す（候補は同じ
     * 航海と港を 2 回使わず、予定はつながるので、行は航海と積地・揚地で区別できる。Bolt 37f の開始準備の決定 (4)。Bolt 37f3）。
     */
    private static String customerLegSummary(ScheduledLeg leg) {
        return "航海 " + leg.voyageNumber() + "、" + leg.load().unLocode() + " から "
                + leg.discharge().unLocode();
    }

    private static MilestoneView milestone(
            TrackingRecord trackingRecord, Milestone milestone, UserId viewer, UtcInstant now) {
        boolean adoptable = false;
        String note = null;
        if (trackingRecord.isSupersededDraft(milestone)) {
            note = SUPERSEDED;
        } else if (milestone.state() == MilestoneState.DRAFT) {
            switch (trackingRecord.adoptCandidate(milestone.milestoneNo(), viewer, now)) {
                case MilestoneAdoption.Adopted adopted -> {
                    adoptable = true;
                    // 採用すると確認中になる候補にもボタンを出し、注記を添える（判定は POST と同じ操作。Bolt 39gc の行 15）
                    note = adopted.milestone().state() == MilestoneState.UNDER_REVIEW
                            ? ADOPTION_PLACES_UNDER_REVIEW
                            : null;
                }
                case MilestoneAdoption.SameAsAdopted(Milestone decided) ->
                    note = decided.state() == MilestoneState.UNDER_REVIEW ? SAME_AS_UNDER_REVIEW : SAME_AS_ADOPTED;
                case MilestoneAdoption.Rejected(AdoptionRejectionReason reason, Optional<Milestone> decided) ->
                    // 組に入らない下書きはない（下書きは外部原本だけが作る）ので、候補でない理由は出さない
                    note = reason == AdoptionRejectionReason.NOT_CANDIDATE
                            ? null
                            : adoptionRejection(reason, decided.map(Milestone::state));
            }
        }
        return new MilestoneView(
                "milestone-" + milestone.milestoneNo(),
                "実績 " + milestone.milestoneNo() + "、" + kind(milestone.kind()) + "、"
                        + milestone.location().unLocode(),
                staff(milestone.occurredAt()),
                source(milestone),
                milestoneState(milestone.state()),
                milestone.milestoneNo(),
                adoptable,
                note);
    }

    /** 出典の表記（「現場記録 F-118（取得 …）」）。結果の文言にも使う。 */
    static String sourceLabel(SourceKind sourceKind, String reference) {
        return sourceKind(sourceKind) + " " + reference;
    }

    private static String source(Milestone milestone) {
        return sourceLabel(milestone.source().kind(), milestone.source().reference()) + "（取得 "
                + staff(milestone.source().acquiredAt()) + "）";
    }

    // C-10 追跡の照会（荷主。Bolt 27）。日時は荷主の日時表示（UTC を併記しない）、経路版・実績番号・実績の状態は出さない

    /**
     * C-10 の一覧の 1 行。
     *
     * @param trackingNumber 追跡番号
     * @param status 現在状態の表示名
     * @param latestEta 最新の到着見込み
     */
    record CustomerRow(String trackingNumber, String status, String latestEta) {}

    static List<CustomerRow> customerList(RecentTrackingRecords recent) {
        return recent.rows().stream()
                .map(summary -> new CustomerRow(
                        summary.trackingNumber().value(),
                        status(summary.currentStatus()),
                        customer(summary.latestEta())))
                .toList();
    }

    /**
     * C-10 の照会の結果。
     *
     * @param trackingNumber 追跡番号
     * @param status 現在状態の表示名
     * @param originalEta 当初の到着予定
     * @param latestEta 最新の到着見込み
     * @param lastAcquiredAt 最終取得時刻（荷主の日時表示。なければ「まだありません」。Bolt 39g3）
     * @param legs 予定区間（区間の順）
     * @param milestones 採用済みの主要実績（発生時刻の順）
     */
    record CustomerDetailView(
            String trackingNumber,
            String status,
            String originalEta,
            String latestEta,
            String lastAcquiredAt,
            List<LegView> legs,
            List<CustomerMilestoneView> milestones) {}

    /**
     * C-10 の主要実績の 1 項目。
     *
     * @param summary 種類・場所
     * @param occurredAt 発生時刻
     * @param source 出典（種類・参照）
     * @param acquiredAt 出典の取得時刻（出典と行を分け、括弧を重ねない。Bolt 27 の開発レビュー）
     */
    record CustomerMilestoneView(String summary, String occurredAt, String source, String acquiredAt) {}

    static CustomerDetailView customerDetail(CustomerTrackingView view) {
        return new CustomerDetailView(
                view.trackingNumber().value(),
                status(view.currentStatus()),
                customer(view.originalEta()),
                customer(view.latestEta()),
                view.lastAcquiredAt().map(TrackingRecordViews::customer).orElse(NO_LAST_ACQUIRED_YET),
                legs(view.legs(), TrackingRecordViews::customer, (legNo, leg) -> customerLegSummary(leg)),
                view.milestones().stream()
                        .map(TrackingRecordViews::customerMilestone)
                        .toList());
    }

    private static CustomerMilestoneView customerMilestone(CustomerMilestone milestone) {
        return new CustomerMilestoneView(
                kind(milestone.kind()) + "、" + milestone.location().unLocode(),
                customer(milestone.occurredAt()),
                sourceLabel(milestone.source().kind(), milestone.source().reference()),
                customer(milestone.source().acquiredAt()));
    }

    private static String customer(UtcInstant instant) {
        return DateTimeDisplay.customer(instant.instant());
    }

    private static String staff(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }
}
