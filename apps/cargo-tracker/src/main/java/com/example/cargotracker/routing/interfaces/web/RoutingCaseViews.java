package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.platform.web.DateTimeDisplay;
import com.example.cargotracker.platform.web.DurationDisplay;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 経路設計の画面（S-05・S-06・S-07）の表示の値と文言（UI 設計。Bolt 17・19）。日時は利用者のタイムゾーン（いまは日本時間）を主にし、
 * UTC を併記する（BR-10、共通部品「日時表示」。platform の Web の部品を使う。Bolt 22）。
 */
final class RoutingCaseViews {

    /** 出発地と目的地の間の言葉。「→」はスクリーンリーダーが「右矢印」と読むので使わない（Bolt 26 の U-4。Bolt 26c）。 */
    private static final String ARROW = " から ";

    private static final String INFO_VERSION = "。参照情報版 ";
    private static final String NO_EXPIRY = "期限の記録なし";

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

    /** 経路候補の比較の案件。 */
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
            List<CandidateView> candidates) {}

    /** 確定した経路（S-06 の確定の後）。 */
    record ConfirmedRouteView(int candidateNo, String approvedBy, String approvedAt, String rationale) {}

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
            boolean confirmed) {}

    /** 区間 1 つ。 */
    record LegView(String voyageNumber, String route, String departure, String arrival) {}

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

    static CaseDetail detail(RoutingCase routingCase, Instant now) {
        RouteVersion version = routingCase.routeVersion();
        boolean confirmable = routingCase.confirmable();
        Integer confirmedNo =
                version.confirmation().map(RouteConfirmation::candidateNo).orElse(null);
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
                        .map(confirmation -> new ConfirmedRouteView(
                                confirmation.candidateNo(),
                                // 画面に内部の ID を出さない（D-4）。名前は利用者の公開 API ができてから引く（Bolt 19 レビュー）
                                "経路設計者",
                                dateTime(confirmation.approvedAt()),
                                confirmation.rationale().text()))
                        .orElse(null),
                version.candidates().stream()
                        .map(candidate -> candidate(
                                candidate,
                                routingCase.specification().arrivalDeadline(),
                                confirmable,
                                Integer.valueOf(candidate.candidateNo()).equals(confirmedNo)))
                        .toList());
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
                candidate(candidate, routingCase.specification().arrivalDeadline(), true, false));
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
            RouteCandidate candidate, UtcInstant deadline, boolean versionConfirmable, boolean confirmed) {
        boolean conforming = candidate.evaluation().conforming();
        return new CandidateView(
                candidate.candidateNo(),
                conforming,
                conforming ? "適合" : "除外",
                routeSummary(candidate.legs()),
                candidate.legs().stream()
                        .map(leg -> new LegView(
                                leg.voyageNumber(),
                                route(leg.load(), leg.discharge()),
                                dateTime(leg.departureAt()),
                                dateTime(leg.arrivalAt())))
                        .toList(),
                dateTime(candidate.evaluation().estimatedArrivalAt()),
                candidate
                        .evaluation()
                        .connectionSlack()
                        .map(RoutingCaseViews::slack)
                        .orElse("直行（積替えなし）"),
                reasons(candidate, deadline),
                dateTime(candidate.oldestInfoAcquiredAt()),
                versionConfirmable && conforming,
                confirmed);
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
                    + "（期限と接続時間を満たす）。参照情報版 "
                    + candidate.legs().stream().map(Leg::infoVersion).distinct().collect(Collectors.joining("、")));
        }
        return texts;
    }

    /** 除外の理由の文言。不適合となった時刻・閾値・参照情報版を示す（R-INV-02）。 */
    static String reason(ExclusionReason reason, List<Leg> legs) {
        return switch (reason.code()) {
            case DEADLINE_EXCEEDED ->
                "期限超過 "
                        + DurationDisplay.connection(Duration.between(
                                reason.deadline().instant(), reason.violatedAt().instant()))
                        + "（到着予定 " + dateTime(reason.violatedAt()) + "、期限 " + dateTime(reason.deadline()) + "）"
                        + INFO_VERSION + reason.infoVersion();
            case CONNECTION_TOO_SHORT ->
                "接続不足（" + reason.port().unLocode() + " で接続 " + connection(reason, legs) + "、必要 "
                        + DurationDisplay.connection(reason.requiredConnection()) + "、"
                        + reason.port().unLocode()
                        + " の規則）。次の出発 " + dateTime(reason.violatedAt()) + INFO_VERSION + reason.infoVersion();
            case NOT_CONNECTABLE ->
                "接続を判定できない（" + reason.port().unLocode() + " の接続時間規則が未登録。接続 "
                        + connection(reason, legs) + "）。次の出発 " + dateTime(reason.violatedAt())
                        + INFO_VERSION + reason.infoVersion();
            case CARGO_NOT_SUPPORTED -> "貨物種別に対応しない" + INFO_VERSION + reason.infoVersion();
            case INFO_INSUFFICIENT -> "情報不足" + INFO_VERSION + reason.infoVersion();
        };
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
