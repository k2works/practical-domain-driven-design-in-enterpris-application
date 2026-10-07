package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 経路設計の画面（S-05・S-06）の表示の値と文言（UI 設計。Bolt 17）。日時は利用者のタイムゾーン（いまは日本時間）を主にし、
 * UTC を併記する（BR-10、共通部品「日時表示」。見積りの画面と同じ形）。
 */
final class RoutingCaseViews {

    /** 画面のタイムゾーン。利用者ごとの設定は後の Bolt で入れる（BR-10）。 */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Tokyo");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm VV（'UTC'xxx）");
    private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");
    private static final String ARROW = " → ";
    private static final String INFO_VERSION = "。参照情報版 ";

    private RoutingCaseViews() {}

    /** 案件一覧の 1 行。 */
    record CaseRow(
            String number,
            String transportRequest,
            String route,
            String arrivalDeadline,
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
            boolean calculated,
            String evaluatedAt,
            List<CandidateView> candidates) {}

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
            String infoAcquiredAt) {}

    /** 区間 1 つ。 */
    record LegView(String voyageNumber, String route, String departure, String arrival) {}

    static CaseRow row(RoutingCaseSummary summary) {
        return new CaseRow(
                summary.number().text(),
                transportRequest(summary.transportRequestNumber(), summary.transportRequestVersionNo()),
                route(summary.origin(), summary.destination()),
                dateTime(summary.arrivalDeadline()),
                dateTime(summary.requestedAt()),
                status(summary.status()));
    }

    static CaseDetail detail(RoutingCase routingCase) {
        RouteVersion version = routingCase.routeVersion();
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
                version.status() != RouteVersionStatus.DRAFT,
                version.evaluatedAt().map(RoutingCaseViews::dateTime).orElse(null),
                version.candidates().stream()
                        .map(candidate ->
                                candidate(candidate, routingCase.specification().arrivalDeadline()))
                        .toList());
    }

    static String status(RouteVersionStatus status) {
        return switch (status) {
            case DRAFT -> "候補の算出待ち";
            // 荷主に提示したと誤読されないよう、確定待ちと示す（Bolt 17 レビュー D-61）
            case CANDIDATES_PRESENTED -> "候補算出済み（確定待ち）";
            case EXPERT_REVIEW -> "専門判断待ち";
            case CONFIRMED -> "確定";
            case REDESIGN_REQUIRED -> "再設計要";
            case SUPERSEDED -> "旧版";
        };
    }

    private static CandidateView candidate(RouteCandidate candidate, UtcInstant deadline) {
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
                dateTime(candidate.oldestInfoAcquiredAt()));
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
                    + duration(Duration.between(
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
                        + duration(Duration.between(
                                reason.deadline().instant(), reason.violatedAt().instant()))
                        + "（到着予定 " + dateTime(reason.violatedAt()) + "、期限 " + dateTime(reason.deadline()) + "）"
                        + INFO_VERSION + reason.infoVersion();
            case CONNECTION_TOO_SHORT ->
                "接続不足（" + reason.port().unLocode() + " で接続 " + connection(reason, legs) + "、必要 "
                        + duration(reason.requiredConnection()) + "、"
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
                return duration(Duration.between(
                        legs.get(i - 1).arrivalAt().instant(),
                        legs.get(i).departureAt().instant()));
            }
        }
        return "不明";
    }

    /** 接続余裕。負なら不足として示す。 */
    static String slack(Duration slack) {
        return slack.isNegative() ? "不足 " + duration(slack.negated()) : "余裕 " + duration(slack);
    }

    /** 期間の文言（例: 1 日 12 時間、4 時間、4 時間 30 分、0 分）。 */
    static String duration(Duration duration) {
        long days = duration.toDays();
        int hours = duration.toHoursPart();
        int minutes = duration.toMinutesPart();
        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + " 日");
        }
        if (hours > 0) {
            parts.add(hours + " 時間");
        }
        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + " 分");
        }
        return String.join(" ", parts);
    }

    /** 社内の画面の日時。利用者のタイムゾーンを主にし、UTC を括弧で併記する。 */
    static String dateTime(UtcInstant instant) {
        return DATE_TIME.format(ZonedDateTime.ofInstant(instant.instant(), DISPLAY_ZONE)) + "（UTC "
                + UTC_TIME.format(instant.instant().atOffset(ZoneOffset.UTC)) + "）";
    }

    private static String transportRequest(String number, int versionNo) {
        return number + " 版 " + versionNo;
    }

    private static String route(Location from, Location to) {
        return from.unLocode() + ARROW + to.unLocode();
    }
}
