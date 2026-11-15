package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryInquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 有人案件を起票すると、その前の顧客 Web の照会が有人対応へ移ったと更新される（起票の共通の手順から。IR-INV-01。W9 の Bolt 44）。 */
class ServiceCaseOpenEscalationTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");
    private static final UserId SUPPORT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000044a0101"));

    private final InMemoryServiceCaseRepository cases = new InMemoryServiceCaseRepository();
    private final InMemoryInquiryRecordRepository inquiries = new InMemoryInquiryRecordRepository();
    private final InMemoryTrackingRecordRepository trackingRecords = new InMemoryTrackingRecordRepository();

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

    private SystemServiceCaseOpener opener() {
        return new SystemServiceCaseOpener(
                cases,
                new CountingServiceCaseNumberIssuer(),
                (from, to) -> calendar(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new WebInquiryEscalator(inquiries, trackingRecords));
    }

    @Test
    void 起票した有人案件の受付番号で直前の顧客_Web_の照会が有人対応へ移ったと更新される() {
        trackingRecords.save(TrackingRecord.start(
                        TrackingFixture.TRACKING_NUMBER,
                        TrackingFixture.BOOKING_ID,
                        TrackingFixture.SHIPPER,
                        TrackingFixture.CONSIGNEE,
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());
        UUID lookupId = UUID.randomUUID();
        inquiries.save(new InquiryRecord(
                lookupId,
                InquiryChannel.WEB_SELF,
                TrackingFixture.SHIPPER,
                Optional.of(TrackingFixture.TRACKING_NUMBER),
                InquirerKind.SHIPPER,
                Optional.of(SUPPORT),
                new UtcInstant(NOW.minusSeconds(3600)),
                Optional.empty(),
                Optional.empty()));
        ServiceCaseDraft draft = ServiceCaseDraft.of(
                        ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "遅れの問い合わせ", "CS")
                .withShipper(TrackingFixture.SHIPPER)
                .withBooking(TrackingFixture.BOOKING_ID);

        OpenServiceCaseOutcome outcome = opener().open(draft, SUPPORT, "TEST:ESCALATION");

        assertThat(outcome).isInstanceOf(OpenServiceCaseOutcome.Opened.class);
        String opened = ((OpenServiceCaseOutcome.Opened) outcome).number().value();
        assertThat(inquiries.records)
                .singleElement()
                .satisfies(inquiry -> assertThat(inquiry.caseNumber())
                        .hasValueSatisfying(number -> assertThat(number.value()).isEqualTo(opened)));
    }
}
