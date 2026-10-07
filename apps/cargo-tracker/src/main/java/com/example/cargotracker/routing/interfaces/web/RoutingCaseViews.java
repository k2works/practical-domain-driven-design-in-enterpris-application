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
                version.candidates().stream().map(RoutingCaseViews::candidate).toList());
    }

    static String status(RouteVersionStatus status) {
        return switch (status) {
            case DRAFT -> "候補の算出待ち";
            case CANDIDATES_PRESENTED -> "候補提示済み";
            case EXPERT_REVIEW -> "専門判断待ち";
            case CONFIRMED -> "確定";
            case REDESIGN_REQUIRED -> "再設計要";
            case SUPERSEDED -> "旧版";
        };
    }

    private static CandidateView candidate(RouteCandidate candidate) {
        boolean conforming = candidate.evaluation().conforming();
        return new CandidateView(
                candidate.candidateNo(),
                conforming,
                conforming ? "適合" : "除外",
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
                reasons(candidate),
                dateTime(candidate.oldestInfoAcquiredAt()));
    }

    private static List<String> reasons(RouteCandidate candidate) {
        List<String> texts = new ArrayList<>();
        for (ExclusionReason reason : candidate.evaluation().reasons()) {
            texts.add(reason(reason));
        }
        if (texts.isEmpty()) {
            Leg last = candidate.legs().getLast();
            texts.add("期限と接続時間を満たす" + INFO_VERSION + last.infoVersion());
        }
        return texts;
    }

    /** 除外の理由の文言。不適合となった時刻・閾値・参照情報版を示す（R-INV-02）。 */
    static String reason(ExclusionReason reason) {
        return switch (reason.code()) {
            case DEADLINE_EXCEEDED ->
                "期限超過 "
                        + duration(Duration.between(
                                reason.deadline().instant(), reason.violatedAt().instant()))
                        + "（到着予定 " + dateTime(reason.violatedAt()) + "、期限 " + dateTime(reason.deadline()) + "）"
                        + INFO_VERSION + reason.infoVersion();
            case CONNECTION_TOO_SHORT ->
                "接続不足（必要 " + duration(reason.requiredConnection()) + "、"
                        + reason.port().unLocode() + " の規則）。次の出発 " + dateTime(reason.violatedAt()) + INFO_VERSION
                        + reason.infoVersion();
            case NOT_CONNECTABLE ->
                "接続できない（" + reason.port().unLocode() + " の接続時間規則がない）。次の出発 " + dateTime(reason.violatedAt())
                        + INFO_VERSION + reason.infoVersion();
            case CARGO_NOT_SUPPORTED -> "貨物種別に対応しない" + INFO_VERSION + reason.infoVersion();
            case INFO_INSUFFICIENT -> "情報不足" + INFO_VERSION + reason.infoVersion();
        };
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
