package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約条件。本予約の確定の時点で見積りの公開 API から写した、輸送要求・見積り・経路版・荷受人・貨物・荷主承認の値（ADR-016）。
 * 予約版はこの写しを持ち、確定の後に見積りへ問い合わせ直さない（B-INV-08）。見積りの内部情報（料金明細）は写さない。
 *
 * @param transportRequestId 輸送要求 ID
 * @param transportRequestVersionNo 輸送要求の版番号
 * @param transportRequestNumber 業務番号の表記
 * @param quotationId 見積り ID
 * @param quotationNo 見積り番号（輸送要求の中で 1 から。業務番号と見積り番号で同じ見積りの予約を引く。Bolt 24）
 * @param shipperCompanyId 荷主企業 ID
 * @param consigneeCompanyId 荷受人企業 ID
 * @param routingCaseNumber 承認済み経路版の案件番号の表記
 * @param routeVersionNo 承認済み経路版の番号
 * @param cargoCategory 貨物区分の名前
 * @param cargoSummary 貨物の要約
 * @param shipperApproverId 承認した荷主担当者の利用者 ID
 */
@ValueObject
public record BookingTerms(
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
        UUID shipperApproverId) {

    public BookingTerms {
        Objects.requireNonNull(transportRequestId, "transportRequestId");
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(quotationId, "quotationId");
        Objects.requireNonNull(shipperCompanyId, "shipperCompanyId");
        Objects.requireNonNull(consigneeCompanyId, "consigneeCompanyId");
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(cargoCategory, "cargoCategory");
        Objects.requireNonNull(cargoSummary, "cargoSummary");
        Objects.requireNonNull(shipperApproverId, "shipperApproverId");
        if (transportRequestVersionNo < 1 || quotationNo < 1 || routeVersionNo < 1) {
            throw new IllegalArgumentException("版番号は 1 以上です");
        }
    }
}
