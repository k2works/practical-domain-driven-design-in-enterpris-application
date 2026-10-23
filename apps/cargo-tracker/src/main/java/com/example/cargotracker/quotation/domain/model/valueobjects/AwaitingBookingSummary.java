package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予約の確定待ちの見積りの 1 行の読み取りモデル（社内の受付一覧 S-02。US-04、Bolt 20 レビュー D-78。Bolt 23b）。
 * 荷主が承認した見積り（見積りの承認済みを正にし、輸送要求の予約待ちへの更新を待たない）と、荷主の承認の後に失効した見積りを
 * 再見積りした後で社内承認して提示する前の見積り（経路を引き継いだ承認待ち。D-85。Bolt 35c）の写しで、業務の規則は持たない。
 * 失効は表示する側が有効期限と判定時刻で決める。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param status 見積りの状態（承認済み、または経路を引き継いだ承認待ち）
 * @param routingCaseNumber 割り当てた経路の案件番号
 * @param routeVersionNo 割り当てた経路版の番号
 * @param shipperApprovedAt 荷主の承認時刻（承認待ちの行は null）
 * @param expiresAt 見積りの有効期限
 * @param routeRedesignRequired 割り当てた経路が再設計要か（行は表に残し、画面が本予約の入口を出さない。US-07 AC5。Bolt 37e3）
 */
public record AwaitingBookingSummary(
        TransportRequestNumber number,
        int quotationNo,
        QuotationStatus status,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant shipperApprovedAt,
        UtcInstant expiresAt,
        boolean routeRedesignRequired) {

    /** 割り当てた経路が再設計要でない行。 */
    public AwaitingBookingSummary(
            TransportRequestNumber number,
            int quotationNo,
            QuotationStatus status,
            String routingCaseNumber,
            int routeVersionNo,
            UtcInstant shipperApprovedAt,
            UtcInstant expiresAt) {
        this(number, quotationNo, status, routingCaseNumber, routeVersionNo, shipperApprovedAt, expiresAt, false);
    }

    public AwaitingBookingSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (status != QuotationStatus.APPROVED && status != QuotationStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("予約の確定待ちの行は承認済みか承認待ちです: " + status);
        }
        if ((status == QuotationStatus.APPROVED) != (shipperApprovedAt != null)) {
            throw new IllegalArgumentException("承認済みの行だけが荷主の承認時刻を持ちます: " + status);
        }
    }
}
