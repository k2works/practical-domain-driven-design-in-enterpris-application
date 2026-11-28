package com.example.cargotracker.quotation.interfaces.api;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;

/**
 * 予約確定に使える見積りの照会の依頼（見積りの公開 API。Bolt 23）。画面の URL に内部の ID を出さないため、業務番号と見積り番号で
 * 見積りを指す（D-4。Bolt 23b で見積り ID から改めた。ADR-016）。操作者の担当企業の外の輸送要求は、ほかの理由より先に範囲の外を返す
 * （Bolt 38f2。US-16 AC4）。公開 API に主体を載せるのは、呼び手の担当範囲で絞るときだけ（ADR-016）。
 *
 * @param transportRequestNumber 業務番号（例: {@code TR-2026-0001}）
 * @param quotationNo 見積り番号（輸送要求の中で 1 から）
 * @param committedAt 予約確定の commit 時刻（判定と記録に同じ値を使う。ADR-016）
 * @param operator 操作者（担当範囲の判定にだけ使う。Bolt 38f2）
 */
public record BookableQuotationRequest(
        String transportRequestNumber, int quotationNo, UtcInstant committedAt, AuthenticatedActor operator) {

    public BookableQuotationRequest {
        Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        Objects.requireNonNull(committedAt, "committedAt");
        Objects.requireNonNull(operator, "operator");
    }
}
