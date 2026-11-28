package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 荷主に見える最新の見積り（提示済みで見積り番号の最大）の 1 行の読み取りモデル（荷主の見積依頼の一覧 C-02。D-85。Bolt 35d）。
 * 予約待ち・荷主承認待ちの見積依頼の状態を、輸送要求の状態の更新（DE-24・DE-04 の受け取り）を待たずに見積りから選ぶために使う。
 * 集約を組み立てず（R-02）、失効と再承認の見積りかは見積りの集約と同じ判定で決める。
 *
 * @param number 業務番号
 * @param status 見積りの状態
 * @param expiresAt 見積りの有効期限
 * @param shipperApprovedAt 荷主の承認時刻（承認していなければ null。再見積りで失効を記録した見積りも承認の記録を残す）
 * @param presentedAt 提示時刻
 * @param respondedAt 荷主の回答の時刻（回答していなければ null）
 */
public record ShipperLatestQuotationSummary(
        TransportRequestNumber number,
        QuotationStatus status,
        UtcInstant expiresAt,
        UtcInstant shipperApprovedAt,
        UtcInstant presentedAt,
        UtcInstant respondedAt) {

    public ShipperLatestQuotationSummary {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(presentedAt, "presentedAt");
    }

    /** 判定時刻に失効しているか（{@code Quotation.isExpiredAt} と同じ判定）。 */
    public boolean isExpiredAt(UtcInstant judgedAt) {
        return status.isExpiredAt(new QuotationExpiry(expiresAt), judgedAt);
    }

    /** 荷主が承認した見積りか（承認の後に失効したものを含む）。 */
    public boolean isShipperApproved() {
        return shipperApprovedAt != null;
    }

    /** 再承認の見積りか（{@code Quotation.isPresentedForReapproval} と同じ判定）。 */
    public boolean isPresentedForReapproval() {
        return Quotation.isReapprovalPresentation(respondedAt, presentedAt);
    }
}
