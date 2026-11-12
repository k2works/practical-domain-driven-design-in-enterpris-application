package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.platform.web.DurationDisplay;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ArrivalDeadline;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.ConnectionRuleMissing;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.MinimumConnection;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.StaleVoyageInfo;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionThreshold.UnsupportedCargo;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteCargoCategory;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLeg;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLegReason;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 経路設計の画面（S-05・S-06・S-07）の表示の値と文言（UI 設計。Bolt 17・19）。日時は利用者のタイムゾーン（いまは日本時間）を主にし、
 * UTC を併記する（BR-10、共通部品「日時表示」。platform の Web の部品を使う。Bolt 22）。
 */
final class RoutingCaseViews {

    /** 出発地と目的地の間の言葉。「→」はスクリーンリーダーが「右矢印」と読むので使わない（Bolt 26 の U-4。Bolt 26c）。 */
    private static final String ARROW = " から ";

    /** 参照情報版（R-INV-02）の画面の語。値（航海の採用情報版）はそのまま示す（Bolt 29c、#46）。 */
    private static final String INFO_VERSION = "。参照した航海情報の版 ";

    private static final String NO_EXPIRY = "期限の記録なし";

    /** 再算出を促す上の文の先頭（一部の候補が古い。n は候補の数。Bolt 39f2 の決定 (3)）。 */
    private static final String STALE_NOTICE_PARTIAL_LEAD = "古くなった候補が %d 件あります。";

    /** 再算出を促す上の文の先頭（適合の候補がすべて古い）。 */
    private static final String STALE_NOTICE_ALL_LEAD = "適合の候補はすべて古くなっています。";

    private static final String STALE_NOTICE_TAIL = "古くなった候補は確定できません。航海の情報が更新された候補は「候補を再算出」して比べ直してください。";

    /** 取得から許容時間を過ぎた区間があるときだけ続ける文（再算出しても変わらないので、取込の依頼が先）。 */
    private static final String STALE_NOTICE_INSUFFICIENT = "取得から許容時間を過ぎた航海は、先に航海の情報の取込をデータ責任者に依頼し、取り込まれたら再算出してください。";

    private RoutingCaseViews() {}

    /** 案件一覧の 1 行。 */
    record CaseRow(
            String number,
            String transportRequest,
            String route,
            String arrivalDeadline,
            String quotationExpiresAt,
            boolean quotationExpired,
            String requestedAt,
            String status) {}

    /**
     * 経路候補の比較の案件。
     *
     * @param staleCandidates 古くなった候補の数（区間ではなく候補を数える。Bolt 39f2）
     * @param allConformingStale 適合の候補が 1 件以上あり、そのすべてが古い
     * @param infoInsufficientStale 取得から許容時間を過ぎた区間がある
     * @param allExcludedForCargo 候補が 1 件以上あり、すべての候補の理由が貨物種別だけ（導入文を手動の窓口の案内に差し替える。Bolt 39i）
     */
    record CaseDetail(
            String number,
            String transportRequest,
            String route,
            String arrivalDeadline,
            String routePolicy,
            String requestedAt,
            String quotationExpiresAt,
            boolean quotationExpired,
            long aggregateVersion,
            boolean calculated,
            boolean confirmable,
            String evaluatedAt,
            int omittedCandidates,
            ConfirmedRouteView confirmedRoute,
            List<CandidateView> candidates,
            int staleCandidates,
            boolean allConformingStale,
            boolean infoInsufficientStale,
            boolean allExcludedForCargo) {

        /** 再算出を促す上の文（再算出は案件に 1 つの操作なので候補ごとに繰り返さない。Bolt 39f2 の決定 (3)）。古くなった候補がなければ null。 */
        public String staleNotice() {
            if (staleCandidates == 0) {
                return null;
            }
            String lead =
                    allConformingStale ? STALE_NOTICE_ALL_LEAD : STALE_NOTICE_PARTIAL_LEAD.formatted(staleCandidates);
            return lead + STALE_NOTICE_TAIL + (infoInsufficientStale ? STALE_NOTICE_INSUFFICIENT : "");
        }
    }

    /**
     * 確定した経路（S-06 の確定の後）。確定の後に再設計要になった経路版では、再設計要にした時刻と理由も示す（Bolt 37d2）。
     *
     * @param redesignRequiredAt 再設計要にした時刻（再設計要でなければ null）
     * @param redesignReasons 再設計要の理由（画面の語。「、」でつなぐ。再設計要でなければ null）
     */
    record ConfirmedRouteView(
            int candidateNo,
            String approvedBy,
            String approvedAt,
            String rationale,
            String redesignRequiredAt,
            String redesignReasons) {

        /** 再設計要か。 */
        public boolean redesignRequired() {
            return redesignRequiredAt != null;
        }

        /** 確定の欄の見出しと候補の印の語（T-148。Bolt 37d2）。 */
        public String label() {
            return redesignRequired() ? "再設計要の経路" : "確定した経路";
        }
    }

    /** 経路の確定（S-07）。 */
    record ConfirmationPage(
            String number,
            String transportRequest,
            String route,
            String arrivalDeadline,
            String quotationExpiresAt,
            boolean quotationExpired,
            long aggregateVersion,
            CandidateView candidate) {}

    /** 候補 1 件。 */
    record CandidateView(
            int candidateNo,
            boolean conforming,
            String judgement,
            String routeSummary,
            List<LegView> legs,
            String estimatedArrival,
            String connectionSlack,
            List<String> reasons,
            String infoAcquiredAt,
            boolean confirmable,
            boolean confirmed,
            boolean judgedAtConfirmation,
            boolean stale) {

        /**
         * 判定の印。再設計要の経路版では、判定が確定のときのものと分かるように示す（Bolt 37d2 レビュー）。古くなった候補では、判定を消さず算出のときのものと分かるように示す（Bolt 39f2）。
         */
        public String judgementBadge() {
            String badge = conforming ? "✓ 適合" : "✕ 除外";
            if (judgedAtConfirmation) {
                return badge + "（確定のとき）";
            }
            return stale ? badge + "（算出のとき）" : badge;
        }
    }

    /**
     * 区間 1 つ。
     *
     * @param staleNote 古くなった区間の文（古くなければ null。Bolt 39f2）
     */
    record LegView(String voyageNumber, String route, String departure, String arrival, String staleNote) {}

    /** 案件一覧の 1 行。見積有効期限を過ぎていれば期限切れと示す（経路設計は期限で確定を拒否しない。Bolt 19）。 */
    static CaseRow row(RoutingCaseSummary summary, Instant now) {
        return new CaseRow(
                summary.number().text(),
                transportRequest(summary.transportRequestNumber(), summary.transportRequestVersionNo()),
                route(summary.origin(), summary.destination()),
                dateTime(summary.arrivalDeadline()),
                summary.expiresAt().map(RoutingCaseViews::dateTime).orElse(NO_EXPIRY),
                summary.expiresAt()
                        .map(expiry -> !now.isBefore(expiry.instant()))
                        .orElse(false),
                dateTime(summary.requestedAt()),
                status(summary.status()));
    }

    /**
     * 経路候補の比較（S-06）の画面。
     *
     * @param staleLegs 開いた時刻の航海で判定した、古くなった区間（Bolt 39f2）
     */
    static CaseDetail detail(RoutingCase routingCase, List<StaleLeg> staleLegs, Instant now) {
        RouteVersion version = routingCase.routeVersion();
        boolean confirmable = routingCase.confirmable();
        Integer confirmedNo =
                version.confirmation().map(RouteConfirmation::candidateNo).orElse(null);
        Map<Integer, Map<Integer, StaleLegReason>> staleByCandidate = staleLegs.stream()
                .collect(Collectors.groupingBy(
                        StaleLeg::candidateNo, Collectors.toMap(StaleLeg::legNo, StaleLeg::reason)));
        List<CandidateView> candidates = version.candidates().stream()
                .map(candidate -> candidate(
                        candidate,
                        routingCase.specification().arrivalDeadline(),
                        confirmable,
                        Integer.valueOf(candidate.candidateNo()).equals(confirmedNo),
                        version.redesign().isPresent(),
                        staleByCandidate.getOrDefault(candidate.candidateNo(), Map.of())))
                .toList();
        return new CaseDetail(
                routingCase.number().text(),
                transportRequest(routingCase.transportRequestNumber(), routingCase.transportRequestVersionNo()),
                route(
                        routingCase.specification().origin(),
                        routingCase.specification().destination()),
                dateTime(routingCase.specification().arrivalDeadline()),
                routingCase.routePolicyVia().isEmpty()
                        ? "経由地の指定なし"
                        : routingCase.routePolicyVia().stream()
                                .map(Location::unLocode)
                                .collect(Collectors.joining("、")),
                dateTime(routingCase.requestedAt()),
                expiresAt(routingCase),
                expired(routingCase, now),
                routingCase.aggregateVersion(),
                version.status() != RouteVersionStatus.DRAFT,
                confirmable,
                version.evaluatedAt().map(RoutingCaseViews::dateTime).orElse(null),
                version.omittedCandidates(),
                version.confirmation()
                        .map(confirmation -> confirmedRoute(confirmation, version.redesign()))
                        .orElse(null),
                candidates,
                staleByCandidate.size(),
                allConformingStale(candidates),
                staleLegs.stream().anyMatch(staleLeg -> staleLeg.reason() == StaleLegReason.INFO_INSUFFICIENT),
                allExcludedForCargo(version.candidates()));
    }

    /** 候補が 1 件以上あり、すべての候補の理由が貨物種別だけか（貨物種別と別の区分が混ざるときは今の導入文のまま。Bolt 39i）。 */
    private static boolean allExcludedForCargo(List<RouteCandidate> candidates) {
        return !candidates.isEmpty()
                && candidates.stream().allMatch(candidate -> {
                    List<ExclusionReason> reasons = candidate.evaluation().reasons();
                    return !reasons.isEmpty()
                            && reasons.stream()
                                    .allMatch(reason -> reason.code() == ExclusionReasonCode.CARGO_NOT_SUPPORTED);
                });
    }

    /** 適合の候補が 1 件以上あり、そのすべてが古いか（適合が 0 件は「一部」の文。行 20b）。 */
    private static boolean allConformingStale(List<CandidateView> candidates) {
        return candidates.stream().anyMatch(CandidateView::conforming)
                && candidates.stream().filter(CandidateView::conforming).allMatch(CandidateView::stale);
    }

    /** 経路の確定（S-07）の画面。 */
    static ConfirmationPage confirmation(RoutingCase routingCase, RouteCandidate candidate, Instant now) {
        return new ConfirmationPage(
                routingCase.number().text(),
                transportRequest(routingCase.transportRequestNumber(), routingCase.transportRequestVersionNo()),
                route(
                        routingCase.specification().origin(),
                        routingCase.specification().destination()),
                dateTime(routingCase.specification().arrivalDeadline()),
                expiresAt(routingCase),
                expired(routingCase, now),
                routingCase.aggregateVersion(),
                candidate(candidate, routingCase.specification().arrivalDeadline(), true, false, false, Map.of()));
    }

    /** 見積有効期限と同時刻または後は期限切れ（見積りの失効 Q-INV-07 と同じ向き）。経路設計は期限で確定を拒否しない。 */
    private static boolean expired(RoutingCase routingCase, Instant now) {
        return routingCase
                .quotationExpiresAt()
                .map(expiry -> !now.isBefore(expiry.instant()))
                .orElse(false);
    }

    private static String expiresAt(RoutingCase routingCase) {
        return routingCase.quotationExpiresAt().map(RoutingCaseViews::dateTime).orElse(NO_EXPIRY);
    }

    private static ConfirmedRouteView confirmedRoute(
            RouteConfirmation confirmation, Optional<RedesignRecord> redesign) {
        return new ConfirmedRouteView(
                confirmation.candidateNo(),
                // 画面に内部の ID を出さない（D-4）。名前は利用者の公開 API ができてから引く（Bolt 19 レビュー）
                "経路設計者",
                dateTime(confirmation.approvedAt()),
                confirmation.rationale().text(),
                redesign.map(RedesignRecord::requiredAt)
                        .map(RoutingCaseViews::dateTime)
                        .orElse(null),
                redesign.map(redesignRecord -> redesignRecord.reasons().stream()
                                .map(RoutingCaseViews::redesignReason)
                                .collect(Collectors.joining("、")))
                        .orElse(null));
    }

    /** 再設計要の理由の画面の語（受入シナリオの語と同じ。Bolt 37d2）。 */
    static String redesignReason(RedesignReasonCode reason) {
        return switch (reason) {
            case DEADLINE_EXCEEDED -> "期限超過";
            case CONNECTION_TOO_SHORT -> "接続不足";
            case NOT_CONNECTABLE -> "接続できない";
            // 「引き直す」は実装の語なので、欠航・寄港の取りやめを利用者の語で示す（Bolt 37d2 レビュー）
            case VOYAGE_UNAVAILABLE -> "航海の予定が使えない";
        };
    }

    static String status(RouteVersionStatus status) {
        return switch (status) {
            case DRAFT -> "候補の算出待ち";
            // 荷主に提示したと誤読されないよう、確定待ちと示す（Bolt 17 レビュー D-61）
            case CANDIDATES_PRESENTED -> "候補算出済み（確定待ち）";
            case EXPERT_REVIEW -> "専門判断待ち";
            case CONFIRMED -> "確定済み";
            case REDESIGN_REQUIRED -> "再設計要";
            case SUPERSEDED -> "旧版";
        };
    }

    private static CandidateView candidate(
            RouteCandidate candidate,
            UtcInstant deadline,
            boolean versionConfirmable,
            boolean confirmed,
            boolean judgedAtConfirmation,
            Map<Integer, StaleLegReason> staleReasons) {
        boolean conforming = candidate.evaluation().conforming();
        boolean stale = !staleReasons.isEmpty();
        return new CandidateView(
                candidate.candidateNo(),
                conforming,
                conforming ? "適合" : "除外",
                routeSummary(candidate.legs()),
                legs(candidate.legs(), staleReasons),
                dateTime(candidate.evaluation().estimatedArrivalAt()),
                candidate
                        .evaluation()
                        .connectionSlack()
                        .map(RoutingCaseViews::slack)
                        .orElse("直行（積替えなし）"),
                reasons(candidate, deadline),
                dateTime(candidate.oldestInfoAcquiredAt()),
                versionConfirmable && conforming && !stale,
                confirmed,
                judgedAtConfirmation,
                stale);
    }

    private static List<LegView> legs(List<Leg> legs, Map<Integer, StaleLegReason> staleReasons) {
        List<LegView> views = new ArrayList<>();
        for (int index = 0; index < legs.size(); index++) {
            Leg leg = legs.get(index);
            StaleLegReason reason = staleReasons.get(index + 1);
            views.add(new LegView(
                    leg.voyageNumber(),
                    route(leg.load(), leg.discharge()),
                    dateTime(leg.departureAt()),
                    dateTime(leg.arrivalAt()),
                    reason == null ? null : staleNote(reason)));
        }
        return views;
    }

    /** 区間の下の文。変わったことだけを示し、今の値は示さない（Bolt 39f2 の決定 (3)）。 */
    static String staleNote(StaleLegReason reason) {
        return switch (reason) {
            case VOYAGE_MISSING -> "今の航海の情報にこの航海がありません。";
            case INFO_VERSION_CHANGED -> "参照した航海情報の版が変わりました。";
            case SCHEDULE_CHANGED -> "参照した航海情報の版は同じですが、この区間の予定が変わりました。";
            case INFO_INSUFFICIENT -> "航海の情報が取得から許容時間を過ぎています。取込をデータ責任者に依頼してください。";
        };
    }

    /** 経由の要約（直行、または積替えの港）。 */
    private static String routeSummary(List<Leg> legs) {
        if (legs.size() == 1) {
            return "直行";
        }
        return legs.subList(0, legs.size() - 1).stream()
                        .map(leg -> leg.discharge().unLocode())
                        .collect(Collectors.joining("・"))
                + " 積替え";
    }

    /**
     * 判定の根拠（適合）か除外の理由の文言。適合は期限までの余裕と、使った航海の採用情報版を示す。
     * 除外は不適合となった時刻・閾値・参照情報版と、接続の理由では実際の接続時間を示す（R-INV-02。Bolt 17 レビュー D-61）。
     */
    private static List<String> reasons(RouteCandidate candidate, UtcInstant deadline) {
        List<String> texts = new ArrayList<>();
        for (ExclusionReason reason : candidate.evaluation().reasons()) {
            texts.add(reason(reason, candidate.legs()));
        }
        if (texts.isEmpty()) {
            texts.add("期限まで "
                    + DurationDisplay.connection(Duration.between(
                            candidate.evaluation().estimatedArrivalAt().instant(), deadline.instant()))
                    + "（期限と接続時間を満たす）"
                    + INFO_VERSION
                    + candidate.legs().stream().map(Leg::infoVersion).distinct().collect(Collectors.joining("、")));
        }
        return texts;
    }

    /** 除外の理由の文言。不適合となった時刻・閾値・参照情報版を示す（R-INV-02）。情報不足は古い航海・許容時間・取得時刻（Bolt 37b2）。 */
    static String reason(ExclusionReason reason, List<Leg> legs) {
        return switch (reason.threshold()) {
            case ArrivalDeadline threshold ->
                "期限超過 "
                        + DurationDisplay.connection(Duration.between(
                                threshold.deadline().instant(),
                                reason.violatedAt().instant()))
                        + "（到着予定 " + dateTime(reason.violatedAt()) + "、期限 " + dateTime(threshold.deadline()) + "）"
                        + INFO_VERSION + reason.infoVersion();
            case MinimumConnection threshold ->
                "接続不足（" + threshold.port().unLocode() + " で接続 " + connection(reason, legs) + "、必要 "
                        + DurationDisplay.connection(threshold.required()) + "、"
                        + threshold.port().unLocode()
                        + " の規則）。次の出発 " + dateTime(reason.violatedAt()) + INFO_VERSION + reason.infoVersion();
            case ConnectionRuleMissing threshold ->
                "接続を判定できない（" + threshold.port().unLocode() + " に次の出発の時刻で有効な接続時間規則がない。接続 "
                        + connection(reason, legs) + "）。次の出発 " + dateTime(reason.violatedAt())
                        + INFO_VERSION + reason.infoVersion();
            case UnsupportedCargo threshold ->
                "貨物種別に対応しない（" + cargoName(threshold.category()) + "。MVP の対象は一般貨物だけで、手動の窓口で扱う）。最初の出発 "
                        + dateTime(reason.violatedAt()) + INFO_VERSION + reason.infoVersion();
            case StaleVoyageInfo threshold ->
                "情報不足（" + threshold.voyageNumber() + " の情報は取得から " + hours(threshold.tolerance())
                        + "を過ぎている）。取得 " + dateTime(reason.infoAcquiredAt().orElseThrow())
                        + INFO_VERSION + reason.infoVersion();
        };
    }

    /** 画面の貨物種別の語（ui_design の語。業務の文の「冷凍貨物」ではなく画面の「冷凍」にそろえる。Bolt 39i）。 */
    private static String cargoName(RouteCargoCategory category) {
        return switch (category) {
            case GENERAL -> "一般";
            case DANGEROUS -> "危険物";
            case REEFER -> "冷凍";
            case OTHER_SPECIAL -> "その他特殊";
            case UNRECOGNIZED -> "不明な貨物種別";
        };
    }

    /** 情報鮮度の許容時間（Bolt 37b2。24 時間を「1 日」とせず時間と分で示す。分未満は示さない）。 */
    private static String hours(Duration duration) {
        return duration.toMinutesPart() == 0 ? duration.toHours() + " 時間" : DurationDisplay.hoursAndMinutes(duration);
    }

    /** 接続の理由の実際の接続時間（前の区間の到着予定から、理由の時刻に出発する区間まで）。 */
    private static String connection(ExclusionReason reason, List<Leg> legs) {
        for (int i = 1; i < legs.size(); i++) {
            if (legs.get(i).departureAt().equals(reason.violatedAt())) {
                return DurationDisplay.connection(Duration.between(
                        legs.get(i - 1).arrivalAt().instant(),
                        legs.get(i).departureAt().instant()));
            }
        }
        return "不明";
    }

    /** 接続余裕。負なら不足として示す。 */
    static String slack(Duration slack) {
        return slack.isNegative()
                ? "不足 " + DurationDisplay.connection(slack.negated())
                : "余裕 " + DurationDisplay.connection(slack);
    }

    /** 社内の画面の日時。利用者のタイムゾーンを主にし、UTC を括弧で併記する（platform の部品。Bolt 22）。 */
    static String dateTime(UtcInstant instant) {
        return DateTimeDisplay.staff(instant.instant());
    }

    private static String transportRequest(String number, int versionNo) {
        return number + " 版 " + versionNo;
    }

    private static String route(Location from, Location to) {
        return from.unLocode() + ARROW + to.unLocode();
    }
}
