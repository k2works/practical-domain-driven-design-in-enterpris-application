package com.example.cargotracker.booking.domain.model.valueobjects;

/**
 * 本予約の確定条件の 1 つ（BR-01）。並びは画面で示す順。
 */
public enum BookingCondition {
    /** 有効な見積り（commit 時刻が有効期限より前の承認済みの見積り。BR-10）。 */
    VALID_QUOTATION,
    /** 必須貨物情報。 */
    REQUIRED_CARGO,
    /** 荷主担当者 1 名の承認。 */
    SHIPPER_APPROVAL,
    /** 承認済み経路版。 */
    APPROVED_ROUTE,
    /** 営業担当者による条件確認。 */
    STAFF_CONFIRMATION
}
