package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.application.internal.commands.RecordInquiryCommand;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingRecordScope;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.CountingServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendar;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessCalendarNotRegisteredException;
import com.example.cargotracker.tracking.domain.model.valueobjects.BusinessDay;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.MissingItem;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 電話・メールの照会の記録と有人案件への引き継ぎ（US-11 AC2、IR-INV-02。Bolt 40-15）。 */
class InquiryRecordingServiceTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
    /** 月 2026-11-16 10:00 JST。 */
    private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

    private static final UserId SUPPORT = new UserId(UUID.fromString("00000000-0000-0000-0000-0000040f1501"));
    private static final TrackingNumber TRACKING = TrackingFixture.TRACKING_NUMBER;

    private final InMemoryTrackingRecordRepository trackingRecords = new InMemoryTrackingRecordRepository();
    private final InMemoryServiceCaseRepository cases = new InMemoryServiceCaseRepository();
    private final List<InquiryRecord> saved = new ArrayList<>();
    private final InquiryRecordRepository inquiries = saved::add;

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

    private InquiryRecordingService service() {
        return new InquiryRecordingService(
                new TrackingRecordScope(trackingRecords),
                inquiries,
                new ServiceCaseCommandService(
                        cases,
                        new CountingServiceCaseNumberIssuer(),
                        (from, to) -> calendar(),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuthenticatedActor support(CompanyId... assigned) {
        return new AuthenticatedActor(
                SUPPORT, TrackingFixture.SHIPPER, Set.of(Role.CUSTOMER_SUPPORT), "窓口", "A 社", Set.of(assigned));
    }

    private void started() {
        trackingRecords.save(TrackingRecord.reconstitute(
                TRACKING,
                TrackingFixture.BOOKING_ID,
                TrackingFixture.SHIPPER,
                TrackingFixture.CONSIGNEE,
                TrackedBookingStatus.CONFIRMED,
                TrackingFixture.schedule(),
                TrackingStatus.PICKUP_SCHEDULED,
                OptionalInt.empty(),
                List.of(),
                TrackingFixture.at("2026-11-15T00:00:00Z"),
                TrackingFixture.at("2026-11-16T03:00:00Z"),
                TrackingFixture.STARTED_AT,
                0));
    }

    private static RecordInquiryCommand command(boolean handOff, String content) {
        return new RecordInquiryCommand(
                InquiryChannel.PHONE,
                TRACKING,
                InquirerKind.SHIPPER,
                Optional.empty(),
                content,
                handOff,
                Optional.empty(),
                "CS");
    }

    @Test
    void 行1_引き継ぎなしは照会記録だけを書き有人案件は起票しない() {
        started();

        RecordInquiryOutcome outcome = service().record(support(TrackingFixture.SHIPPER), command(false, "到着予定の確認だけ"));

        assertThat(outcome).isInstanceOf(RecordInquiryOutcome.Recorded.class);
        assertThat(((RecordInquiryOutcome.Recorded) outcome).caseNumber()).isEmpty();
        assertThat(saved).singleElement().satisfies(record -> {
            assertThat(record.channel()).isEqualTo(InquiryChannel.PHONE);
            assertThat(record.shipperCompanyId()).isEqualTo(TrackingFixture.SHIPPER);
            assertThat(record.trackingNumber()).contains(TRACKING);
            assertThat(record.inquirerKind()).isEqualTo(InquirerKind.SHIPPER);
            assertThat(record.receivedAt()).isEqualTo(new UtcInstant(NOW));
            assertThat(record.escalated()).isFalse();
            assertThat(record.recordedBy()).contains(SUPPORT);
        });
        assertThat(cases.cases).isEmpty();
    }

    @Test
    void 行2_引き継ぎは有人案件を起票し受付番号と期限を返し照会記録に受付番号を残す() {
        started();
        RecordInquiryCommand command = new RecordInquiryCommand(
                InquiryChannel.EMAIL,
                TRACKING,
                InquirerKind.CONSIGNEE,
                Optional.of(new UtcInstant(Instant.parse("2026-11-16T00:30:00Z"))),
                "貨物の所在が分からない",
                true,
                Optional.of("現場記録 F-118"),
                "運用");

        RecordInquiryOutcome outcome = service().record(support(TrackingFixture.SHIPPER), command);

        RecordInquiryOutcome.Recorded recorded = (RecordInquiryOutcome.Recorded) outcome;
        assertThat(recorded.caseNumber()).isPresent();
        assertThat(recorded.existingCase()).isFalse();
        assertThat(recorded.answerDueAt()).isPresent();
        assertThat(recorded.nextUpdateAt()).isPresent();
        assertThat(cases.cases.values()).singleElement().satisfies(serviceCase -> {
            assertThat(serviceCase.number()).isEqualTo(recorded.caseNumber().orElseThrow());
            assertThat(serviceCase.kind()).isEqualTo(ServiceCaseKind.CUSTOMER_INQUIRY);
            assertThat(serviceCase.department()).isEqualTo("運用");
            assertThat(serviceCase.shipperCompanyId()).contains(TrackingFixture.SHIPPER);
            assertThat(serviceCase.trackingNumber()).contains(TRACKING);
            assertThat(serviceCase.cause()).isEqualTo("貨物の所在が分からない");
            assertThat(serviceCase.sources())
                    .singleElement()
                    .satisfies(source -> assertThat(source.reference()).isEqualTo("現場記録 F-118"));
        });
        assertThat(saved).singleElement().satisfies(record -> {
            assertThat(record.escalated()).isTrue();
            assertThat(record.caseNumber()).contains(recorded.caseNumber().orElseThrow());
            assertThat(record.inquirerKind()).isEqualTo(InquirerKind.CONSIGNEE);
            assertThat(record.receivedAt()).isEqualTo(new UtcInstant(Instant.parse("2026-11-16T00:30:00Z")));
        });
    }

    @Test
    void 行3_未終結の同じ内容の案件があれば新しく起票せず既存の受付番号に引き継ぐ() {
        started();
        RecordInquiryOutcome.Recorded first = (RecordInquiryOutcome.Recorded)
                service().record(support(TrackingFixture.SHIPPER), command(true, "貨物の所在が分からない"));

        RecordInquiryOutcome.Recorded second = (RecordInquiryOutcome.Recorded)
                service().record(support(TrackingFixture.SHIPPER), command(true, "貨物の所在が分からない"));

        assertThat(cases.cases).hasSize(1);
        assertThat(second.caseNumber()).isEqualTo(first.caseNumber());
        assertThat(second.existingCase()).isTrue();
        assertThat(saved)
                .hasSize(2)
                .allSatisfy(record -> assertThat(record.escalated()).isTrue());
    }

    @Test
    void 行4_担当企業の外とない追跡番号は同じ結果で何も書かない() {
        started();
        CompanyId other = new CompanyId(UUID.fromString("00000000-0000-0000-0000-0000040f1502"));

        assertThat(service().record(support(other), command(true, "内容")))
                .isEqualTo(new RecordInquiryOutcome.TrackingNotFound());
        assertThat(service().record(support(), command(true, "内容")))
                .isEqualTo(new RecordInquiryOutcome.TrackingNotFound());
        RecordInquiryCommand unknown = new RecordInquiryCommand(
                InquiryChannel.PHONE,
                new TrackingNumber("CTZZZZZZZZZZZZ"),
                InquirerKind.SHIPPER,
                Optional.empty(),
                "内容",
                true,
                Optional.empty(),
                "CS");
        assertThat(service().record(support(TrackingFixture.SHIPPER), unknown))
                .isEqualTo(new RecordInquiryOutcome.TrackingNotFound());
        assertThat(saved).isEmpty();
        assertThat(cases.cases).isEmpty();
    }

    @Test
    void 行5_引き継ぐのに内容が空なら何も書かず不足を返す() {
        started();

        RecordInquiryOutcome outcome = service().record(support(TrackingFixture.SHIPPER), command(true, "  "));

        assertThat(outcome).isEqualTo(new RecordInquiryOutcome.Incomplete(Set.of(MissingItem.CAUSE)));
        assertThat(saved).isEmpty();
        assertThat(cases.cases).isEmpty();
    }

    @Test
    void 行6_営業カレンダーが未登録なら何も書かない() {
        started();
        InquiryRecordingService noCalendar = new InquiryRecordingService(
                new TrackingRecordScope(trackingRecords),
                inquiries,
                new ServiceCaseCommandService(
                        cases,
                        new CountingServiceCaseNumberIssuer(),
                        (from, to) -> {
                            throw new BusinessCalendarNotRegisteredException(from);
                        },
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(noCalendar.record(support(TrackingFixture.SHIPPER), command(true, "内容")))
                .isEqualTo(new RecordInquiryOutcome.CalendarNotRegistered());
        assertThat(saved).isEmpty();
        assertThat(cases.cases).isEmpty();
    }

    @Test
    void 行7_カスタマーサポートでなければ権限なし() {
        started();
        AuthenticatedActor sales = new AuthenticatedActor(
                SUPPORT, TrackingFixture.SHIPPER, Set.of(Role.SALES), "営業", "A 社", Set.of(TrackingFixture.SHIPPER));

        assertThat(service().record(sales, command(false, "内容"))).isEqualTo(new RecordInquiryOutcome.NotPermitted());
        assertThat(saved).isEmpty();
    }
}
