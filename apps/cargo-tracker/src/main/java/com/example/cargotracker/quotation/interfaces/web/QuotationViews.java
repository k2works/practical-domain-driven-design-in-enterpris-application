package com.example.cargotracker.quotation.interfaces.web;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.quotation.domain.model.valueobjects.PricingBasis;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationExpiry;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RoutePolicy;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 見積りの表示の部品（社内の S-04 と荷主の C-04 で共通。US-03 AC1・AC2）。日時の表し方（荷主は利用者のタイムゾーン、
 * 社内は UTC を併記）だけを呼び出し側が渡す。金額は 3 桁ごとの区切りと小数点以下 2 桁に、通貨を添える（例: 3,730.00 USD）。
 */
final class QuotationViews {

    private QuotationViews() {}

    /** 見積りの表記（例: TR-2026-0001 見積 1）。画面に内部の ID を出さない（D-4 と同じ考え方）。 */
    static String label(TransportRequestNumber number, int quotationNo) {
        return number.text() + " 見積 " + quotationNo;
    }

    /** 見積りを見る人。荷主の画面では、社内の言葉の「置換済み」を荷主向けの言葉にする（Bolt 11 レビュー R-33、D-37）。 */
    enum Audience {
        STAFF,
        CUSTOMER
    }

    /** 表示する時刻で失効していれば「失効」、そうでなければ保存されている状態の表示名（社内の受付一覧 S-02 で使う）。 */
    static String effectiveStatus(QuotationStatus status, UtcInstant expiresAt, UtcInstant now) {
        boolean expired = isExpired(status, expiresAt, now);
        if (expired && status == QuotationStatus.APPROVED) {
            // 荷主が承認した事実を消さない（Bolt 20 レビュー）
            return status(QuotationStatus.EXPIRED) + "（荷主承認済み）";
        }
        return status(expired ? QuotationStatus.EXPIRED : status);
    }

    /** 表示する時刻で失効しているか（有効期限と同時刻以後。Q-INV-06）。S-02 の状態の表示名と、予約の確定待ちの入口の判定で共有する。 */
    static boolean isExpired(QuotationStatus status, UtcInstant expiresAt, UtcInstant now) {
        return status.expiresByTime() && !new QuotationExpiry(expiresAt).isValidAt(now);
    }

    static String status(QuotationStatus status) {
        return switch (status) {
            case DRAFT -> "作成中";
            case PENDING_APPROVAL -> "承認待ち";
            case PRESENTED -> "提示済み";
            case ROUTING_REQUESTED -> "詳細設計依頼済み";
            case AWAITING_SHIPPER_APPROVAL -> "荷主承認待ち";
            case APPROVED -> "荷主承認済み（予約待ち）";
            case EXPIRED -> "失効";
            case REPLACED -> "置換済み";
        };
    }

    /**
     * 見積りの表示。料金根拠・有効期限・経路方針がそろった見積り（承認待ち以後）だけを渡す。
     * 状態は表示する時刻で判定し、承認待ち・提示済みでも有効期限を過ぎていれば「失効」と示す（Q-INV-07。Bolt 11）。
     * 提示できるか・再見積りできるかは集約に問い合わせる（Bolt 11 レビュー R-07）。
     *
     * @param now 表示する時刻（失効の判定時刻）
     * @param audience 見る人（荷主なら置換済みを荷主向けの言葉にする）
     */
    static View view(Quotation quotation, Function<UtcInstant, String> dateTime, UtcInstant now, Audience audience) {
        PricingBasis basis = quotation.pricingBasis().orElseThrow();
        RoutePolicy policy = quotation.routePolicy().orElseThrow();
        String currency = basis.currency().name();
        boolean expired = quotation.isExpiredAt(now);
        boolean replaced = quotation.status() == QuotationStatus.REPLACED;
        String status = statusLabel(quotation.status(), expired, audience);
        return new View(
                quotation.quotationNo(),
                status,
                "見積 " + quotation.quotationNo() + "（" + status + "）",
                basis.lines().stream()
                        .map(line -> new Line(
                                line.description(),
                                money(line.amount(), currency),
                                line.reference().orElse("")))
                        .toList(),
                money(basis.total(), currency),
                dateTime.apply(quotation.expiry().orElseThrow().expiresAt()),
                policy.via().isEmpty()
                        ? "直行（経由地なし）"
                        : policy.via().stream().map(Location::unLocode).collect(Collectors.joining("、")),
                dateTime.apply(policy.departureAt()),
                dateTime.apply(policy.arrivalAt()),
                quotation.presentRejectionAt(now).isEmpty(),
                quotation.requoteRejection().isEmpty() && quotation.status() != QuotationStatus.DRAFT,
                replaced || expired,
                replaced,
                expired,
                quotation.responseRejectionAt(now).isEmpty(),
                quotation.status().isRoutingStarted(),
                quotation.status() == QuotationStatus.ROUTING_REQUESTED,
                quotation.respondedAt().map(dateTime).orElse(null),
                quotation.assignedRoute().map(route -> route(route, dateTime)).orElse(null),
                quotation.status() == QuotationStatus.AWAITING_SHIPPER_APPROVAL,
                quotation.approvalRejectionAt(now).isEmpty(),
                quotation
                        .shipperApproval()
                        .map(approval -> dateTime.apply(approval.approvedAt()))
                        .orElse(null));
    }

    /** 割り当てた経路の表示（Bolt 20）。 */
    static RouteView route(AssignedRoute route, Function<UtcInstant, String> dateTime) {
        return new RouteView(
                route.routingCaseNumber() + " 経路版 " + route.routeVersionNo(),
                dateTime.apply(route.confirmedAt()),
                route.legs().stream()
                        .map(leg -> new LegView(
                                leg.voyageNumber(),
                                leg.load().unLocode() + " → " + leg.discharge().unLocode(),
                                dateTime.apply(leg.departureAt()),
                                dateTime.apply(leg.arrivalAt())))
                        .toList(),
                dateTime.apply(route.arrivalAt()));
    }

    /** 状態の表示名。失効していれば「失効」、荷主に見せる置換済みは「新しい見積りに置き換え」（D-37）。 */
    private static String statusLabel(QuotationStatus status, boolean expired, Audience audience) {
        if (expired) {
            return status(QuotationStatus.EXPIRED);
        }
        if (status == QuotationStatus.REPLACED && audience == Audience.CUSTOMER) {
            return "新しい見積りに置き換え";
        }
        if (audience == Audience.CUSTOMER) {
            switch (status) {
                case ROUTING_REQUESTED -> {
                    return "詳細経路設計を依頼済み";
                }
                case AWAITING_SHIPPER_APPROVAL -> {
                    return "見積りと経路の承認待ち";
                }
                case APPROVED -> {
                    return "承認済み";
                }
                default -> {
                    // 社内と同じ表示名
                }
            }
        }
        return status(status);
    }

    static String money(BigDecimal amount, String currency) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(amount) + " " + currency;
    }

    /**
     * 見積りの表示。
     *
     * @param quotationNo 見積り番号
     * @param status 状態の表示名（表示する時刻で失効していれば「失効」）
     * @param title 見積りの見出し（例: 見積 1（置換済み））
     * @param lines 料金明細
     * @param total 合計（通貨つき）
     * @param expiresAt 有効期限
     * @param via 主な経由地
     * @param departureAt 概算の出発日時
     * @param arrivalAt 概算の到着日時
     * @param presentable 社内承認して提示できるか（承認待ちで、失効していない）
     * @param requotable 再見積りできるか（承認待ち・提示済み。失効したものを含む）
     * @param readOnly 読み取り専用か（置換済み・失効）
     * @param replaced 置換済みか（荷主には新しい見積りを準備中と案内する。D-37）
     * @param expired 表示する時刻で失効しているか（失効を記録したものを含む）
     * @param respondable 荷主が回答できるか（提示済みで、失効していない。Bolt 12）
     * @param routingStarted 荷主が詳細経路設計を依頼した後か（詳細設計依頼済み・荷主承認待ち・承認済み。Bolt 12・20）
     * @param routingInProgress 詳細設計依頼済み（経路設計の途中）か（Bolt 20）
     * @param respondedAt 回答時刻（荷主が回答していなければ null）
     * @param route 割り当てた経路（割り当てていなければ null。Bolt 20）
     * @param awaitingApproval 荷主承認待ちか（Bolt 20）
     * @param approvable 荷主が承認できるか（荷主承認待ちで、失効していない。Bolt 20）
     * @param approvedAt 荷主の承認時刻（承認していなければ null。Bolt 20）
     */
    record View(
            int quotationNo,
            String status,
            String title,
            List<Line> lines,
            String total,
            String expiresAt,
            String via,
            String departureAt,
            String arrivalAt,
            boolean presentable,
            boolean requotable,
            boolean readOnly,
            boolean replaced,
            boolean expired,
            boolean respondable,
            boolean routingStarted,
            boolean routingInProgress,
            String respondedAt,
            RouteView route,
            boolean awaitingApproval,
            boolean approvable,
            String approvedAt) {}

    /**
     * 割り当てた経路の表示（Bolt 20）。
     *
     * @param label 案件番号と経路版（例: RC-2026-0001 経路版 1）
     * @param confirmedAt 経路を確定した日時
     * @param legs 区間
     * @param arrivalAt 到着予定（最後の区間の到着予定）
     */
    record RouteView(String label, String confirmedAt, List<LegView> legs, String arrivalAt) {}

    /**
     * 割り当てた区間の表示（Bolt 20）。
     *
     * @param voyageNumber 航海番号
     * @param ports 積地 → 揚地
     * @param departureAt 出発予定
     * @param arrivalAt 到着予定
     */
    record LegView(String voyageNumber, String ports, String departureAt, String arrivalAt) {}

    /**
     * 料金明細の 1 行の表示。
     *
     * @param description 内容
     * @param amount 金額（通貨つき）
     * @param contractReference 参照した契約条件（なければ空）
     */
    record Line(String description, String amount, String contractReference) {}
}
