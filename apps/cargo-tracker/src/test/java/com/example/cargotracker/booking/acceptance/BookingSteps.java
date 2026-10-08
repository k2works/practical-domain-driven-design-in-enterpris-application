package com.example.cargotracker.booking.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.entities.BookingVersion;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaStatus;
import com.example.cargotracker.quotation.domain.events.QuotationApprovedByShipper;
import com.example.cargotracker.quotation.domain.events.QuotationRouteAssigned;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.ScenarioContext;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 本予約の確定（US-04 AC1・AC2、BR-01、BR-10、B-INV-01・10、DE-07）のステップ定義。予約の入力ポートだけを呼び、予約に使う見積りの
 * ID は見積りの公表された言語（DE-04 荷主が承認した）から得る（AT-05）。
 */
public class BookingSteps {

    private static final CompanyId A_COMPANY = new CompanyId(UUID.fromString("00000000-0000-0000-0000-00000000a000"));
    private static final AuthenticatedActor SALES = actor("00000000-0000-0000-0000-000000000201", Role.SALES, "佐藤");
    private static final AuthenticatedActor SUPPORT =
            actor("00000000-0000-0000-0000-000000000202", Role.CUSTOMER_SUPPORT, "鈴木");

    private final BookingCommandService commandService;
    private final InMemoryBookingRepository bookings;
    private final InMemoryBookingSagaRepository sagas;
    private final DeferredEventDelivery delivery;
    private final ScenarioContext context;
    private BookingConfirmationOutcome lastOutcome;

    public BookingSteps(
            BookingCommandService commandService,
            InMemoryBookingRepository bookings,
            InMemoryBookingSagaRepository sagas,
            DeferredEventDelivery delivery,
            ScenarioContext context) {
        this.commandService = commandService;
        this.bookings = bookings;
        this.sagas = sagas;
        this.delivery = delivery;
        this.context = context;
    }

    @もし("営業担当者が見積り {int} の確定条件を確認して本予約を確定する")
    public void 確定条件を確認して確定する(int quotationNo) {
        lastOutcome = commandService.confirm(new ConfirmBookingCommand(approvedQuotation(quotationNo), SALES, true));
    }

    @もし("営業担当者が見積り {int} の確定条件を確認せずに本予約を確定する")
    public void 確認せずに確定する(int quotationNo) {
        lastOutcome = commandService.confirm(new ConfirmBookingCommand(approvedQuotation(quotationNo), SALES, false));
    }

    @もし("カスタマーサポートが見積り {int} の本予約を確定する")
    public void カスタマーサポートが確定する(int quotationNo) {
        lastOutcome = commandService.confirm(new ConfirmBookingCommand(approvedQuotation(quotationNo), SUPPORT, true));
    }

    @ならば("本予約の結果は {string} である")
    public void 本予約の結果(String result) {
        switch (result) {
            case "確定した" -> assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
            case "失効していて再見積りが必要" -> assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.Expired.class);
            case "見積りが荷主の承認済みでない" ->
                assertThat(lastOutcome)
                        .isEqualTo(new BookingConfirmationOutcome.QuotationUnavailable(
                                QuotationUnavailability.NOT_APPROVED));
            case "確定条件が足りない" ->
                assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.MissingConditions.class);
            case "すでに予約がある" -> assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.AlreadyBooked.class);
            case "確定する権限がない" -> assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.Forbidden.class);
            default -> throw new IllegalArgumentException("未知の結果: " + result);
        }
    }

    @ならば("予約版 {int} と形式どおりの追跡番号と業務番号 {string} と commit 時刻 {string} と確定者が記録される")
    public void 予約が記録される(int versionNo, String transportRequestNumber, String committedAt) {
        Booking booking = confirmedBooking();
        assertThat(booking.trackingNumber().value()).matches("CT[A-HJKMNP-Z2-9]{12}");
        assertThat(booking.transportRequestNumber()).isEqualTo(transportRequestNumber);
        BookingVersion version = booking.currentVersion();
        assertThat(version.versionNo()).isEqualTo(versionNo);
        assertThat(version.committedAt()).isEqualTo(new UtcInstant(Instant.parse(committedAt)));
        assertThat(version.confirmedBy()).isEqualTo(SALES.userId().value());
        assertThat(version.terms().routingCaseNumber()).isEqualTo("RC-2026-0001");
        assertThat(version.terms().routeVersionNo()).isEqualTo(1);
    }

    @ならば("予約サガは {string} で追跡の開始を待つ")
    public void 予約サガの状態(String status) {
        BookingSagaStatus expected =
                switch (status) {
                    case "処理中" -> BookingSagaStatus.IN_PROGRESS;
                    case "完了" -> BookingSagaStatus.COMPLETED;
                    default -> throw new IllegalArgumentException("未知の予約サガの状態: " + status);
                };
        assertThat(sagas.findByBookingId(confirmedBooking().id()))
                .hasValueSatisfying(saga -> assertThat(saga.status()).isEqualTo(expected));
    }

    @ならば("本予約を確定したイベントが {int} 回だけ発行されている")
    public void 確定したイベントの回数(int times) {
        assertThat(delivery.published())
                .filteredOn(BookingConfirmed.class::isInstance)
                .hasSize(times)
                .allSatisfy(event -> assertThat(((BookingConfirmed) event).trackingNumber())
                        .isEqualTo(confirmedBooking().trackingNumber().value()));
    }

    @ならば("予約は記録されていない")
    public void 予約は記録されていない() {
        assertThat(bookings.findAll()).isEmpty();
        assertThat(delivery.published()).noneMatch(BookingConfirmed.class::isInstance);
    }

    private Booking confirmedBooking() {
        assertThat(lastOutcome).isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
        return bookings.findByTrackingNumber(((BookingConfirmationOutcome.Confirmed) lastOutcome).trackingNumber())
                .orElseThrow();
    }

    /**
     * このシナリオの輸送要求の見積り番号の見積りの ID。見積りの公表された言語（DE-21 経路版を割り当てた、DE-04 荷主が承認した）から得る
     * （AT-05）。ほかのシナリオの見積りを拾わないよう輸送要求で絞り、見つからなければ前提の誤りとして失敗させる（Bolt 23 レビュー L-5）。
     */
    private UUID approvedQuotation(int quotationNo) {
        UUID transportRequestId = context.transportRequestId();
        return delivery.history().stream()
                .<UUID>mapMulti((event, ids) -> {
                    if (event instanceof QuotationApprovedByShipper approved
                            && approved.transportRequestId().equals(transportRequestId)
                            && approved.quotationNo() == quotationNo) {
                        ids.accept(approved.quotationId());
                    } else if (event instanceof QuotationRouteAssigned assigned
                            && assigned.transportRequestId().equals(transportRequestId)
                            && assigned.quotationNo() == quotationNo) {
                        ids.accept(assigned.quotationId());
                    }
                })
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("見積り " + quotationNo + " の経路の割り当ても荷主の承認もない"));
    }

    private static AuthenticatedActor actor(String userId, Role role, String name) {
        return new AuthenticatedActor(new UserId(UUID.fromString(userId)), A_COMPANY, Set.of(role), name, "A 社");
    }
}
