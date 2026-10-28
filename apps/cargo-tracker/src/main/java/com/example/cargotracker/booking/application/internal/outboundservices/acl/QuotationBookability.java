package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 見積りの公開 API（予約確定に使える見積りの照会）を呼び、予約の予約条件か使えない理由に変える腐敗防止層（ADR-016。Bolt 23）。
 * 見積りの型（公開 API の戻り値）は、この部品の外に出さない。操作者を公開 API に渡し、担当企業の外は使えない理由ではなく範囲の外と
 * 写す（Bolt 38f2。理由に寄せると未承認の文言に化けるため）。
 */
public class QuotationBookability {

    private final BookableQuotationQuery query;

    public QuotationBookability(BookableQuotationQuery query) {
        this.query = query;
    }

    /**
     * 見積りが commit 時刻に本予約の確定に使えるかを、行を押さえずに確かめる（S-09 の GET）。
     *
     * @param transportRequestNumber 見積りの業務番号
     * @param quotationNo 見積り番号
     * @param committedAt commit 時刻（判定と記録に同じ値を使う）
     * @param operator 操作者（担当範囲の判定に使う。Bolt 38f2）
     * @return 使えるなら予約条件、範囲の外なら範囲の外、使えないなら理由
     */
    public Result check(
            String transportRequestNumber, int quotationNo, UtcInstant committedAt, AuthenticatedActor operator) {
        return toResult(
                query.find(new BookableQuotationRequest(transportRequestNumber, quotationNo, committedAt, operator)));
    }

    /**
     * 本予約の確定のトランザクションの中で、見積りの行を押さえて確かめる（ADR-016。Bolt 37e2）。S-09 の GET は {@link #check} を使う。
     *
     * @param transportRequestNumber 見積りの業務番号
     * @param quotationNo 見積り番号
     * @param committedAt commit 時刻（判定と記録に同じ値を使う）
     * @param operator 操作者（担当範囲の判定に使う。範囲の外なら見積りの行を押さえない。Bolt 38f2）
     * @return 使えるなら予約条件、範囲の外なら範囲の外、使えないなら理由
     */
    public Result checkForConfirmation(
            String transportRequestNumber, int quotationNo, UtcInstant committedAt, AuthenticatedActor operator) {
        return toResult(query.findForConfirmation(
                new BookableQuotationRequest(transportRequestNumber, quotationNo, committedAt, operator)));
    }

    private static Result toResult(BookableQuotationResult result) {
        return switch (result) {
            case BookableQuotationResult.Bookable bookable ->
                new Result.Bookable(toTerms(bookable), bookable.shipperApprovedAt(), bookable.expiresAt());
            case BookableQuotationResult.NotBookable notBookable ->
                new Result.Unavailable(toUnavailability(notBookable.reason()));
            case BookableQuotationResult.OutOfScope(String transportRequestNumber, int quotationNo) ->
                new Result.OutOfScope(transportRequestNumber, quotationNo);
        };
    }

    private static BookingTerms toTerms(BookableQuotationResult.Bookable bookable) {
        return new BookingTerms(
                bookable.transportRequestId(),
                bookable.transportRequestVersionNo(),
                bookable.transportRequestNumber(),
                bookable.quotationId(),
                bookable.quotationNo(),
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
            case BookableQuotationResult.NotBookable.ROUTE_ALREADY_DEPARTED ->
                QuotationUnavailability.ROUTE_ALREADY_DEPARTED;
            case BookableQuotationResult.NotBookable.ROUTE_REDESIGN_REQUIRED ->
                QuotationUnavailability.ROUTE_REDESIGN_REQUIRED;
            case BookableQuotationResult.NotBookable.NOT_APPROVED -> QuotationUnavailability.NOT_APPROVED;
            // 公開 API の定数はすべて上で写す。見積りが理由を足して予約が知らないときだけここに来る（S-09 を 500 にしない。Bolt 37e3 の
            // 状態の組み合わせの行 13）。担当企業の外は理由ではなく別の結果なので、ここには来ない（Bolt 38f2）
            default -> QuotationUnavailability.NOT_APPROVED;
        };
    }

    /** 確かめた結果。 */
    public sealed interface Result {

        /** 確定に使えない結果（使えない・範囲の外。Bolt 38f2 の開発レビューで、拒否だけを受ける口のために分けた）。 */
        sealed interface Refusal extends Result permits Unavailable, OutOfScope {}

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
        record Unavailable(QuotationUnavailability reason) implements Refusal {

            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }

        /**
         * 見積りの輸送要求が操作者の担当企業の外（Bolt 38f2）。
         *
         * @param transportRequestNumber 見積りの公開 API が返す正規の業務番号（記録の ID に使う。予約は業務番号を解釈しない）
         * @param quotationNo 見積り番号
         */
        record OutOfScope(String transportRequestNumber, int quotationNo) implements Refusal {

            public OutOfScope {
                Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
            }
        }
    }
}
