package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.RejectedMilestone;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneRejectionReason;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackedBookingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 拒否した主要実績から有人例外案件を起票する（T-INV-04。Bolt 40-13）。利用者の操作でなくシステムの起票。 */
class RejectedMilestoneCaseOpenerTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    /** 月 2026-11-16 10:00 JST。 */
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

    private static final UserId STEWARD = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040d1301"));
    private static final Source SOURCE = new Source(
            SourceKind.EXTERNAL_RECORD,
            "11111111-1111-1111-1111-111111111111",
            new UtcInstant(Instant.parse("2026-11-16T00:30:00Z")));

    private final InMemoryServiceCaseRepository repository = new InMemoryServiceCaseRepository();
    private final CountingServiceCaseNumberIssuer issuer = new CountingServiceCaseNumberIssuer();

    private static BusinessCalendar calendar() {
        List<BusinessDay> days = new ArrayList<>();
        for (LocalDate date = LocalDate.of(2026, 11, 1);
                !date.isAfter(LocalDate.of(2027, 1, 31));
                date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            days.add(
                    weekend
                            ? BusinessDay.closed(date, JST, null)
                            : BusinessDay.open(date, LocalTime.of(9, 0), LocalTime.of(18, 0), JST, null));
        }
        return BusinessCalendar.of(days);
    }

    private RejectedMilestoneCaseOpener opener() {
        return new RejectedMilestoneCaseOpener(new ServiceCaseCommandService(
                repository, issuer, (from, to) -> calendar(), Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private static RejectedMilestone rejected(MilestoneRejectionReason reason, String trackingNumber) {
        return new RejectedMilestone(
                UUID.randomUUID(),
                new TrackingNumber(trackingNumber),
                MilestoneKind.DEPARTURE,
                new Location("JPTYO"),
                new UtcInstant(Instant.parse("2026-11-15T14:00:00Z")),
                SOURCE,
                UUID.fromString("00000000-0000-0000-0000-0000040d1302"),
                "MS-0001",
                reason,
                new UtcInstant(NOW));
    }

    private static TrackingRecord cancelledRecord() {
        return TrackingRecord.reconstitute(
                TrackingFixture.TRACKING_NUMBER,
                TrackingFixture.BOOKING_ID,
                TrackingFixture.SHIPPER,
                TrackingFixture.CONSIGNEE,
                TrackedBookingStatus.CANCELLED,
                TrackingFixture.schedule(),
                TrackingStatus.PICKUP_SCHEDULED,
                OptionalInt.empty(),
                List.of(),
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
                TrackingFixture.STARTED_AT,
                0);
    }

    @Test
    void 行1_知らない追跡番号は追跡番号と出典を持つ拒否した実績の案件を荷主なしで起票する() {
        opener().open(
                        rejected(MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER, "CTZZZZZZZZZZZZ"),
                        Optional.empty(),
                        STEWARD);

        assertThat(repository.cases.values()).singleElement().satisfies(serviceCase -> {
            assertThat(serviceCase.kind()).isEqualTo(ServiceCaseKind.REJECTED_MILESTONE);
            assertThat(serviceCase.priority()).isEqualTo(ServicePriority.NORMAL);
            assertThat(serviceCase.status()).isEqualTo(ServiceCaseStatus.RECEIVED);
            assertThat(serviceCase.department()).isEqualTo("追跡管理部");
            assertThat(serviceCase.trackingNumber()).contains(new TrackingNumber("CTZZZZZZZZZZZZ"));
            assertThat(serviceCase.sources()).containsExactly(SOURCE);
            assertThat(serviceCase.shipperCompanyId()).isEmpty();
            assertThat(serviceCase.bookingId()).isEmpty();
            assertThat(serviceCase.cause()).contains("UNKNOWN_TRACKING_NUMBER").contains(SOURCE.reference());
        });
    }

    @Test
    void 行2_取消済みの予約は追跡記録の荷主企業と予約を案件に残す() {
        TrackingRecord record = cancelledRecord();

        opener().open(
                        rejected(
                                MilestoneRejectionReason.BOOKING_CANCELLED,
                                record.trackingNumber().value()),
                        Optional.of(record),
                        STEWARD);

        ServiceCase serviceCase = repository.cases.values().iterator().next();
        assertThat(serviceCase.shipperCompanyId()).contains(record.shipperCompanyId());
        assertThat(serviceCase.bookingId()).contains(record.bookingId());
        assertThat(serviceCase.cause()).contains("BOOKING_CANCELLED");
    }

    @Test
    void 行3_同じ拒否の二重の起票は案件を増やさない() {
        RejectedMilestone rejected = rejected(MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER, "CTZZZZZZZZZZZZ");

        opener().open(rejected, Optional.empty(), STEWARD);
        opener().open(rejected, Optional.empty(), STEWARD);

        assertThat(repository.cases).hasSize(1);
    }

    @Test
    void 行4_営業カレンダーが未登録なら例外を上げる() {
        RejectedMilestoneCaseOpener opener = new RejectedMilestoneCaseOpener(new ServiceCaseCommandService(
                repository,
                issuer,
                (from, to) -> {
                    throw new BusinessCalendarNotRegisteredException(from);
                },
                Clock.fixed(NOW, ZoneOffset.UTC)));

        assertThatThrownBy(() -> opener.open(
                        rejected(MilestoneRejectionReason.UNKNOWN_TRACKING_NUMBER, "CTZZZZZZZZZZZZ"),
                        Optional.empty(),
                        STEWARD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("営業カレンダー");
        assertThat(repository.cases).isEmpty();
    }
}
