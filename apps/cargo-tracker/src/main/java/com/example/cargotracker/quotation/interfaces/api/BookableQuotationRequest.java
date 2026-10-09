package com.example.cargotracker.quotation.interfaces.api;

import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予約確定に使える見積りの照会の依頼（見積りの公開 API。Bolt 23）。画面の URL に内部の ID を出さないため、業務番号と見積り番号で
 * 見積りを指す（D-4。Bolt 23b で見積り ID から改めた。ADR-016）。
 *
 * @param transportRequestNumber 業務番号（例: {@code TR-2026-0001}）
 * @param quotationNo 見積り番号（輸送要求の中で 1 から）
 * @param committedAt 予約確定の commit 時刻（判定と記録に同じ値を使う。ADR-016）
 */
public record BookableQuotationRequest(String transportRequestNumber, int quotationNo, UtcInstant committedAt) {

    public BookableQuotationRequest {
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(committedAt, "committedAt");
    }
}
