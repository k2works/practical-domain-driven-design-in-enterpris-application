package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約確定に使える見積りの照会の結果（入力ポートの戻り値。公開 API の型は、アダプターがこれから作る。2026-10-09）。
 * 集約を持たず、確定に要る値の写しだけを持つ（見積りの内部情報の料金明細は含めない）。
 */
public sealed interface BookableQuotation {

    /**
     * 予約確定に使える。確定に要る値の写し。
     *
     * @param transportRequestId 輸送要求 ID
     * @param transportRequestVersionNo 輸送要求の版番号
     * @param transportRequestNumber 業務番号の表記
     * @param quotationId 見積り ID
     * @param quotationNo 見積り番号
     * @param shipperCompanyId 荷主企業 ID
     * @param consigneeCompanyId 荷受人企業 ID
     * @param routingCaseNumber 承認済み経路版の案件番号の表記
     * @param routeVersionNo 承認済み経路版の番号
     * @param cargoCategory 貨物種別の名前
     * @param cargoSummary 貨物の要約
     * @param shipperApproverId 承認した荷主担当者の利用者 ID
     * @param shipperApprovedAt 荷主の承認時刻
     * @param expiresAt 見積有効期限
     */
    record Bookable(
            UUID transportRequestId,
            int transportRequestVersionNo,
            String transportRequestNumber,
            UUID quotationId,
            int quotationNo,
            CompanyId shipperCompanyId,
            CompanyId consigneeCompanyId,
            String routingCaseNumber,
            int routeVersionNo,
            String cargoCategory,
            String cargoSummary,
            UUID shipperApproverId,
            UtcInstant shipperApprovedAt,
            UtcInstant expiresAt)
            implements BookableQuotation {

        public Bookable {
            Objects.requireNonNull(transportRequestId, "transportRequestId");
            Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
            Objects.requireNonNull(quotationId, "quotationId");
            Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
            Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
            Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
            Objects.requireNonNull(cargoCategory, "cargoCategory");
            Objects.requireNonNull(cargoSummary, "cargoSummary");
            Objects.requireNonNull(shipperApproverId, "shipperApproverId");
            Objects.requireNonNull(shipperApprovedAt, "shipperApprovedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    /**
     * 予約確定に使えない（業務の理由）。
     *
     * @param reason 理由
     */
    record NotBookable(Reason reason) implements BookableQuotation {

        public NotBookable {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** 使えない理由。 */
    enum Reason {
        /** 見積りが見つからない。 */
        QUOTATION_NOT_FOUND,
        /** 失効（BR-10）。 */
        EXPIRED,
        /** 置換済み。 */
        REPLACED,
        /** 荷主の承認済みでない。 */
        NOT_APPROVED
    }
}
