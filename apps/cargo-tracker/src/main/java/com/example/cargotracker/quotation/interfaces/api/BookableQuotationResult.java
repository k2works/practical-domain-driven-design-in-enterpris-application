package com.example.cargotracker.quotation.interfaces.api;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 予約確定に使える見積りの照会の結果（見積りの公開 API。ADR-016。Bolt 23）。
 */
public sealed interface BookableQuotationResult {

    /**
     * 予約確定に使える。確定に要る値の写し（見積りの内部情報の料金明細は含めない）。
     *
     * @param transportRequestId 輸送要求 ID
     * @param transportRequestVersionNo 輸送要求の版番号
     * @param transportRequestNumber 業務番号の表記
     * @param quotationId 見積り ID
     * @param quotationNo 見積り番号（輸送要求の中で 1 から）
     * @param shipperCompanyId 荷主企業 ID
     * @param consigneeCompanyId 荷受人企業 ID
     * @param routingCaseNumber 承認済み経路版の案件番号の表記
     * @param routeVersionNo 承認済み経路版の番号
     * @param cargoCategory 貨物種別の名前（GENERAL・DANGEROUS・REEFER・OTHER_SPECIAL のどれか。予約はこの名前が GENERAL かどうかで本予約を
     *     確定できるかを判定するので、名前を変えるときは予約の {@code BookableCargoPolicy} も直す。B-INV-04、Bolt 35）
     * @param cargoSummary 貨物の要約（種別・荷姿・個数・総重量・容積）。表示用の文字列で、書式は見積りが決め、予約は解釈しない（Bolt 29c で日本語名と表示の書式にした）
     * @param shipperApproverId 承認した荷主担当者の利用者 ID
     * @param shipperApprovedAt 荷主の承認時刻（S-09 の確定条件の表に示す。Bolt 23b）
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
            implements BookableQuotationResult {

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
     * @param reason 理由の名前（このレコードの定数のどれか。公開 API の契約で、見積りのドメインの値の名前とは独立に決める）
     */
    record NotBookable(String reason) implements BookableQuotationResult {

        /** 見積りが見つからない。 */
        public static final String QUOTATION_NOT_FOUND = "QUOTATION_NOT_FOUND";

        /** commit 時刻が有効期限と同時刻以後、または失効を記録した（BR-10。再見積りが必要）。 */
        public static final String EXPIRED = "EXPIRED";

        /** 置換済み（新しい見積りを使う）。 */
        public static final String REPLACED = "REPLACED";

        /** 荷主の承認済みでない。 */
        public static final String NOT_APPROVED = "NOT_APPROVED";

        /** 割り当てた経路版が再設計要（B-INV-05。確定した後に外部情報の更新で条件を満たさなくなった。Bolt 37e）。 */
        public static final String ROUTE_REDESIGN_REQUIRED = "ROUTE_REDESIGN_REQUIRED";

        public NotBookable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
