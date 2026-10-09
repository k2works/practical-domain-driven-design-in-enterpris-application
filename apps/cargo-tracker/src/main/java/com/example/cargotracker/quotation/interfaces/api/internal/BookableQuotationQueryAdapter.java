package com.example.cargotracker.quotation.interfaces.api.internal;

import com.example.cargotracker.quotation.application.internal.queryservices.BookableQuotation;
import com.example.cargotracker.quotation.application.internal.queryservices.BookableQuotationQueryService;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationQuery;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.interfaces.api.BookableQuotationResult;
import org.springframework.stereotype.Component;

/**
 * 見積りの公開 API（予約確定に使える見積りの照会）のインバウンドアダプター（ADR-016。Bolt 23 の実装を 2026-10-09 に interfaces.api へ
 * 移した）。照会の入力ポートに委ね、確定に要る値の写しを公開 API の型に変える。理由はドメインの値の名前ではなく公開 API の定数で返す。
 */
@Component
public class BookableQuotationQueryAdapter implements BookableQuotationQuery {

    private final BookableQuotationQueryService service;

    public BookableQuotationQueryAdapter(BookableQuotationQueryService service) {
        this.service = service;
    }

    @Override
    public BookableQuotationResult find(BookableQuotationRequest request) {
        return switch (service.find(request.transportRequestNumber(), request.quotationNo(), request.committedAt())) {
            case BookableQuotation.Bookable bookable -> toResult(bookable);
            case BookableQuotation.NotBookable(BookableQuotation.Reason reason) ->
                new BookableQuotationResult.NotBookable(reasonOf(reason));
        };
    }

    private static BookableQuotationResult toResult(BookableQuotation.Bookable bookable) {
        return new BookableQuotationResult.Bookable(
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
                bookable.shipperApproverId(),
                bookable.shipperApprovedAt(),
                bookable.expiresAt());
    }

    private static String reasonOf(BookableQuotation.Reason reason) {
        return switch (reason) {
            case QUOTATION_NOT_FOUND -> BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND;
            case EXPIRED -> BookableQuotationResult.NotBookable.EXPIRED;
            case REPLACED -> BookableQuotationResult.NotBookable.REPLACED;
            case NOT_APPROVED -> BookableQuotationResult.NotBookable.NOT_APPROVED;
        };
    }
}
