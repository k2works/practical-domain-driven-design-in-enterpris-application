package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 経路設計中の見積依頼の、詳細設計依頼済みの見積りの 1 行の読み取りモデル（社内の受付一覧 S-02。US-24 AC1。Bolt 12）。
 * 集約を組み立てずに一覧するための写しで、業務の規則は持たない。失効は表示する側が有効期限と判定時刻で決める。
 *
 * @param number 業務番号
 * @param quotationNo 詳細設計依頼済み・荷主承認待ちの見積りの見積り番号
 * @param status 見積りの状態（Bolt 20 で荷主承認待ちも並べる。承認済みは Bolt 23b で予約の確定待ちの表に移した）
 * @param requestedAt 依頼時刻（荷主の回答時刻）
 * @param expiresAt 見積りの有効期限
 * @param routeConfirmedAt 割り当てた経路の確定の時刻（荷主承認待ち。詳細設計依頼済みは null。Bolt 20 レビュー D-78。Bolt 23b）
 */
public record RoutingRequestedSummary(
        TransportRequestNumber number,
        int quotationNo,
        QuotationStatus status,
        UtcInstant requestedAt,
        UtcInstant expiresAt,
        UtcInstant routeConfirmedAt) {

    public RoutingRequestedSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
