package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 確定条件（BR-01、B-INV-01。Bolt 23）。欠けた条件を不足条件として返す。 */
class BookingConditionsTest {

    @Test
    void 五つの条件がそろえば不足条件はない() {
        assertThat(new BookingConditions(true, true, true, true, true).missing())
                .isEmpty();
    }

    @Test
    void 欠けた条件を決めた順で返す() {
        assertThat(new BookingConditions(false, true, false, true, false).missing())
                .containsExactly(
                        BookingCondition.VALID_QUOTATION,
                        BookingCondition.SHIPPER_APPROVAL,
                        BookingCondition.STAFF_CONFIRMATION);
        assertThat(new BookingConditions(true, false, true, false, true).missing())
                .containsExactly(BookingCondition.REQUIRED_CARGO, BookingCondition.APPROVED_ROUTE);
    }
}
