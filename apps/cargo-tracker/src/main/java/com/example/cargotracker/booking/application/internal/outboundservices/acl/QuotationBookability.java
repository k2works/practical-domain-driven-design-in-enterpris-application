package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.quotation.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 見積りの公開 API（予約確定に使える見積りの照会）を呼び、予約の予約条件か使えない理由に変える腐敗防止層（ADR-016。Bolt 23）。
 * 見積りの型（公開 API の戻り値）は、この部品の外に出さない。
 */
public class QuotationBookability {

    private final BookableQuotationQuery query;

    public QuotationBookability(BookableQuotationQuery query) {
        this.query = query;
    }

    /**
     * 見積りが commit 時刻に本予約の確定に使えるかを確かめる。
     *
     * @param transportRequestNumber 見積りの業務番号
     * @param quotationNo 見積り番号
     * @param committedAt commit 時刻（判定と記録に同じ値を使う）
     * @return 使えるなら予約条件、使えないなら理由
     */
    public Result check(String transportRequestNumber, int quotationNo, UtcInstant committedAt) {
        return switch (query.find(new BookableQuotationRequest(transportRequestNumber, quotationNo, committedAt))) {
            case BookableQuotationResult.Bookable bookable ->
                new Result.Bookable(toTerms(bookable), bookable.shipperApprovedAt(), bookable.expiresAt());
            case BookableQuotationResult.NotBookable notBookable ->
                new Result.Unavailable(toUnavailability(notBookable.reason()));
        };
    }

    private static BookingTerms toTerms(BookableQuotationResult.Bookable bookable) {
        return new BookingTerms(
                bookable.transportRequestId(),
                bookable.transportRequestVersionNo(),
                bookable.transportRequestNumber(),
                bookable.quotationId(),
                bookable.shipperCompanyId(),
                bookable.consigneeCompanyId(),
                bookable.routingCaseNumber(),
                bookable.routeVersionNo(),
                bookable.cargoCategory(),
                bookable.cargoSummary(),
                bookable.shipperApproverId());
    }

    private static QuotationUnavailability toUnavailability(String reason) {
        return switch (reason) {
            case BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND -> QuotationUnavailability.NOT_FOUND;
            case BookableQuotationResult.NotBookable.EXPIRED -> QuotationUnavailability.EXPIRED;
            case BookableQuotationResult.NotBookable.REPLACED -> QuotationUnavailability.REPLACED;
            default -> QuotationUnavailability.NOT_APPROVED;
        };
    }

    /** 確かめた結果。 */
    public sealed interface Result {

        /**
         * 使える。
         *
         * @param terms 予約条件（見積りの写し）
         * @param shipperApprovedAt 荷主の承認時刻（S-09 の確定条件の表に示す。Bolt 23b）
         * @param expiresAt 見積りの有効期限（S-09 に示す）
         */
        record Bookable(BookingTerms terms, UtcInstant shipperApprovedAt, UtcInstant expiresAt) implements Result {

            public Bookable {
                Objects.requireNonNull(terms, "terms");
                Objects.requireNonNull(shipperApprovedAt, "shipperApprovedAt");
                Objects.requireNonNull(expiresAt, "expiresAt");
            }
        }

        /**
         * 使えない。
         *
         * @param reason 理由
         */
        record Unavailable(QuotationUnavailability reason) implements Result {

            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
