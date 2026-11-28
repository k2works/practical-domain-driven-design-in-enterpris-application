package com.example.cargotracker.tracking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.acceptance.InMemoryTrackingRecordRepository;
import com.example.cargotracker.tracking.domain.model.TrackingFixture;
import com.example.cargotracker.tracking.domain.model.aggregates.InMemoryInquiryRecordRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.InquiryRecord;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquirerKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.InquiryChannel;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseDraft;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServicePriority;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 顧客 Web の照会の後 24 時間以内に同じ予約の有人案件が起票されたら、有人対応へ移ったと更新する（IR-INV-01。KPI-02。W9 の Bolt 44。台帳 D-170。仮）。
 * 24 時間ちょうど前の照会は含む（「24 時間以内」）。
 */
class WebInquiryEscalatorTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-16T09:00:00Z");
    private static final CaseNumber CASE = CaseNumber.of(2026, 1);
    private static final UserId USER = new UserId(UUID.fromString("00000000-0000-0000-0000-0000044a0001"));

    private final InMemoryInquiryRecordRepository inquiries = new InMemoryInquiryRecordRepository();
    private final InMemoryTrackingRecordRepository trackingRecords = new InMemoryTrackingRecordRepository();
    private final WebInquiryEscalator escalator = new WebInquiryEscalator(inquiries, trackingRecords);

    private void trackingRecordExists() {
        trackingRecords.save(TrackingRecord.start(
                        TrackingFixture.TRACKING_NUMBER,
                        TrackingFixture.BOOKING_ID,
                        TrackingFixture.SHIPPER,
                        TrackingFixture.CONSIGNEE,
                        TrackingFixture.schedule(),
                        TrackingFixture.STARTED_AT)
                .trackingRecord());
    }

    private static ServiceCaseDraft draftFor(CompanyId shipper, UUID bookingId) {
        return ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "遅れの問い合わせ", "CS")
                .withShipper(shipper)
                .withBooking(bookingId);
    }

    private InquiryRecord lookup(InquiryChannel channel, CompanyId shipper, TrackingNumber tracking, Duration before) {
        InquiryRecord lookup = new InquiryRecord(
                UUID.randomUUID(),
                channel,
                shipper,
                Optional.of(tracking),
                InquirerKind.SHIPPER,
                Optional.of(USER),
                new UtcInstant(OPENED_AT.minus(before)),
                Optional.empty(),
                Optional.empty());
        inquiries.save(lookup);
        return lookup;
    }

    private void open(ServiceCaseDraft draft) {
        escalator.onCaseOpened(draft, CASE, new UtcInstant(OPENED_AT));
    }

    private boolean escalated(InquiryRecord lookup) {
        return inquiries.records.stream()
                .filter(r -> r.id().equals(lookup.id()))
                .findFirst()
                .orElseThrow()
                .caseNumber()
                .filter(CASE::equals)
                .isPresent();
    }

    @Test
    void 照会の後_24時間以内に同じ予約の有人案件が起票されたら有人対応へ移ったと更新する() {
        trackingRecordExists();
        InquiryRecord within = lookup(
                InquiryChannel.WEB_SELF, TrackingFixture.SHIPPER, TrackingFixture.TRACKING_NUMBER, Duration.ofHours(3));

        open(draftFor(TrackingFixture.SHIPPER, TrackingFixture.BOOKING_ID));

        assertThat(escalated(within)).isTrue();
    }

    @Test
    void 二十四時間ちょうど前の照会は含み_1秒古い照会は含まない() {
        trackingRecordExists();
        InquiryRecord exactly = lookup(
                InquiryChannel.WEB_SELF,
                TrackingFixture.SHIPPER,
                TrackingFixture.TRACKING_NUMBER,
                Duration.ofHours(24));
        InquiryRecord older = lookup(
                InquiryChannel.WEB_SELF,
                TrackingFixture.SHIPPER,
                TrackingFixture.TRACKING_NUMBER,
                Duration.ofHours(24).plusSeconds(1));

        open(draftFor(TrackingFixture.SHIPPER, TrackingFixture.BOOKING_ID));

        assertThat(escalated(exactly)).isTrue();
        assertThat(escalated(older)).isFalse();
    }

    @Test
    void 別の荷主企業と別の追跡番号と電話の照会と移った後の照会は更新しない() {
        trackingRecordExists();
        CompanyId otherShipper = new CompanyId(UUID.randomUUID());
        InquiryRecord otherCompany =
                lookup(InquiryChannel.WEB_SELF, otherShipper, TrackingFixture.TRACKING_NUMBER, Duration.ofHours(1));
        InquiryRecord otherTracking = lookup(
                InquiryChannel.WEB_SELF,
                TrackingFixture.SHIPPER,
                new TrackingNumber("CTZZZZZZZZZZ22"),
                Duration.ofHours(1));
        InquiryRecord phone = lookup(
                InquiryChannel.PHONE, TrackingFixture.SHIPPER, TrackingFixture.TRACKING_NUMBER, Duration.ofHours(1));
        InquiryRecord alreadyEscalated = lookup(
                InquiryChannel.WEB_SELF, TrackingFixture.SHIPPER, TrackingFixture.TRACKING_NUMBER, Duration.ofHours(2));
        inquiries.escalate(alreadyEscalated.id(), CaseNumber.of(2026, 99));

        open(draftFor(TrackingFixture.SHIPPER, TrackingFixture.BOOKING_ID));

        assertThat(escalated(otherCompany)).isFalse();
        assertThat(escalated(otherTracking)).isFalse();
        assertThat(escalated(phone)).isFalse();
        assertThat(escalated(alreadyEscalated)).isFalse();
    }

    @Test
    void 予約か荷主企業のない案件と追跡記録のない予約は何も更新しない() {
        trackingRecordExists();
        InquiryRecord within = lookup(
                InquiryChannel.WEB_SELF, TrackingFixture.SHIPPER, TrackingFixture.TRACKING_NUMBER, Duration.ofHours(1));

        open(ServiceCaseDraft.of(ServiceCaseKind.QUOTATION_CONSULTATION, ServicePriority.NORMAL, "相談", "営業部")
                .withShipper(TrackingFixture.SHIPPER));
        open(draftFor(TrackingFixture.SHIPPER, UUID.randomUUID()));
        open(ServiceCaseDraft.of(ServiceCaseKind.CUSTOMER_INQUIRY, ServicePriority.NORMAL, "遅れ", "CS")
                .withBooking(TrackingFixture.BOOKING_ID));

        assertThat(escalated(within)).isFalse();
    }
}
