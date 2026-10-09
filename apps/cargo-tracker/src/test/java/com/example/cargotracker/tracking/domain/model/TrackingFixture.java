package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 追跡の単体テストの値（Bolt 25）。東京 → 釜山 → ロサンゼルスの 2 区間。 */
public final class TrackingFixture {

    public static final TrackingNumber TRACKING_NUMBER = new TrackingNumber("CTABCDEFGH2345");
    public static final UUID BOOKING_ID = UUID.fromString("00000000-0000-0000-0000-000000002501");
    public static final CompanyId SHIPPER = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002511"));
    public static final CompanyId CONSIGNEE = new CompanyId(UUID.fromString("00000000-0000-0000-0000-000000002512"));
    public static final UtcInstant STARTED_AT = at("2026-10-26T01:00:00Z");

    private TrackingFixture() {}

    public static UtcInstant at(String instant) {
        return new UtcInstant(Instant.parse(instant));
    }

    public static ScheduledLeg leg(String voyage, String load, String discharge, String departure, String arrival) {
        return new ScheduledLeg(voyage, new Location(load), new Location(discharge), at(departure), at(arrival));
    }

    public static List<ScheduledLeg> twoLegs() {
        return List.of(
                leg("V100", "JPTYO", "KRPUS", "2026-11-01T00:00:00Z", "2026-11-03T00:00:00Z"),
                leg("V200", "KRPUS", "USLAX", "2026-11-04T00:00:00Z", "2026-11-15T00:00:00Z"));
    }

    public static Schedule schedule() {
        return new Schedule("RC-2026-0001", 1, twoLegs());
    }
}
