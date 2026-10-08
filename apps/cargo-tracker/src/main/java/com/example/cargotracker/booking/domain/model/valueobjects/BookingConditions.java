package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 確定条件。本予約の確定に要る 5 条件がそろっているか（BR-01、B-INV-01）。見積りと経路版の状態はアプリケーションサービスが
 * 見積りの公開 API から得て値として渡す（ADR-016）。
 *
 * @param validQuotation 有効な見積り
 * @param requiredCargo 必須貨物情報
 * @param shipperApproval 荷主担当者 1 名の承認
 * @param approvedRoute 承認済み経路版
 * @param staffConfirmation 営業担当者による条件確認
 */
@ValueObject
public record BookingConditions(
        boolean validQuotation,
        boolean requiredCargo,
        boolean shipperApproval,
        boolean approvedRoute,
        boolean staffConfirmation) {

    /** 欠けた条件（不足条件）。{@link BookingCondition} の順。そろっていれば空。 */
    public List<BookingCondition> missing() {
        List<BookingCondition> missing = new ArrayList<>();
        if (!validQuotation) {
            missing.add(BookingCondition.VALID_QUOTATION);
        }
        if (!requiredCargo) {
            missing.add(BookingCondition.REQUIRED_CARGO);
        }
        if (!shipperApproval) {
            missing.add(BookingCondition.SHIPPER_APPROVAL);
        }
        if (!approvedRoute) {
            missing.add(BookingCondition.APPROVED_ROUTE);
        }
        if (!staffConfirmation) {
            missing.add(BookingCondition.STAFF_CONFIRMATION);
        }
        return List.copyOf(missing);
    }
}
