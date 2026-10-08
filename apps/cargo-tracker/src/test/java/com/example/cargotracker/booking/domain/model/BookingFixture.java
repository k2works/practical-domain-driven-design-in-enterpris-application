package com.example.cargotracker.booking.domain.model;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingConditions;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.UUID;

/** 予約の単体テストの値。 */
public final class BookingFixture {

    public static final UUID SHIPPER_COMPANY = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    public static final UUID TRANSPORT_REQUEST = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    public static final UUID QUOTATION = UUID.fromString("00000000-0000-0000-0000-00000000b001");
    public static final UUID CONSIGNEE_COMPANY = UUID.fromString("00000000-0000-0000-0000-00000000c002");
    public static final UUID SHIPPER_APPROVER = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    public static final UUID SALES = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    public static final TrackingNumber TRACKING_NUMBER = new TrackingNumber("CTABCDEFGH2345");

    private BookingFixture() {}

    public static BookingTerms terms() {
        return new BookingTerms(
                TRANSPORT_REQUEST,
                2,
                "TR-2026-0001",
                QUOTATION,
                new CompanyId(SHIPPER_COMPANY),
                new CompanyId(CONSIGNEE_COMPANY),
                "RC-2026-0001",
                1,
                "GENERAL",
                "一般貨物 パレット 10 個 1,200 kg",
                SHIPPER_APPROVER);
    }

    public static BookingConditions allConditions() {
        return new BookingConditions(true, true, true, true, true);
    }
}
