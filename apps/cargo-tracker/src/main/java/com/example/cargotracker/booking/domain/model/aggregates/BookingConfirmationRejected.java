package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import java.util.List;

/**
 * 本予約の確定を拒否した（B-INV-01。Bolt 23）。欠けた確定条件（不足条件）をすべて値で持ち、画面がまとめて示す（US-04 AC3。Bolt 35）。
 * 特殊貨物の拒否は {@link SpecialCargoRejected}。
 */
public final class BookingConfirmationRejected extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<BookingCondition> missingConditions;

    public BookingConfirmationRejected(List<BookingCondition> missingConditions) {
        super("確定条件がそろっていません: " + missingConditions);
        this.missingConditions = List.copyOf(missingConditions);
    }

    public List<BookingCondition> missingConditions() {
        return missingConditions;
    }
}
