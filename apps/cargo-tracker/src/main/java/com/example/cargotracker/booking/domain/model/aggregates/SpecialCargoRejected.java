package com.example.cargotracker.booking.domain.model.aggregates;

/**
 * 特殊貨物を含む見積りの本予約の確定を拒否した（B-INV-04、US-04 AC5。Bolt 35）。直せる不足条件（{@link BookingConfirmationRejected}）
 * とは分ける。
 */
public final class SpecialCargoRejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SpecialCargoRejected(String cargoCategory) {
        super("特殊貨物を含む見積りは本予約を確定できません: " + cargoCategory);
    }
}
