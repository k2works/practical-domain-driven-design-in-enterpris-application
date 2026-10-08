package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予約の確定待ちの見積りの 1 行の読み取りモデル（社内の受付一覧 S-02。US-04、Bolt 20 レビュー D-78。Bolt 23b）。
 * 荷主が承認した見積り（見積りの承認済みを正にし、輸送要求の予約待ちへの更新を待たない）の写しで、業務の規則は持たない。
 * 失効は表示する側が有効期限と判定時刻で決める。
 *
 * @param number 業務番号
 * @param quotationNo 見積り番号
 * @param routingCaseNumber 割り当てた経路の案件番号
 * @param routeVersionNo 割り当てた経路版の番号
 * @param shipperApprovedAt 荷主の承認時刻
 * @param expiresAt 見積りの有効期限
 */
public record AwaitingBookingSummary(
        TransportRequestNumber number,
        int quotationNo,
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant shipperApprovedAt,
        UtcInstant expiresAt) {

    public AwaitingBookingSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(shipperApprovedAt, "shipperApprovedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
