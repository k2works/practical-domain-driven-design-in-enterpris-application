package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 見積提示済みの見積依頼の、最新の見積りの 1 行の読み取りモデル（社内の受付一覧 S-02。Bolt 11 レビュー R-02）。
 * 集約を組み立てずに一覧するための写しで、業務の規則は持たない。失効は表示する側が有効期限と判定時刻で決める。
 *
 * @param number 業務番号
 * @param quotationNo 最新の見積りの見積り番号
 * @param status 最新の見積りの状態（保存されている状態）
 * @param expiresAt 最新の見積りの有効期限
 */
public record QuotedRequestSummary(
        TransportRequestNumber number, int quotationNo, QuotationStatus status, UtcInstant expiresAt) {

    public QuotedRequestSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
