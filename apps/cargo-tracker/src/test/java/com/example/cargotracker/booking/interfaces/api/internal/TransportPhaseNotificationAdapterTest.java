package com.example.cargotracker.booking.interfaces.api.internal;

import static com.example.cargotracker.booking.domain.model.BookingFixture.SALES;
import static com.example.cargotracker.booking.domain.model.BookingFixture.TRACKING_NUMBER;
import static com.example.cargotracker.booking.domain.model.BookingFixture.allConditions;
import static com.example.cargotracker.booking.domain.model.BookingFixture.processedCommand;
import static com.example.cargotracker.booking.domain.model.BookingFixture.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.application.internal.commandservices.TransportPhaseCommandService;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportPhase;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationReceipt;
import com.example.cargotracker.booking.interfaces.api.TransportPhaseNotificationRequest;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 予約の公開 API（輸送段階の通知）のインバウンドアダプター（Bolt 39g2 の行 8。ADR-014）。入力ポートに委ね、結果を公開 API の受領の型に変える
 * （予約の型を公開 API の外に出さない。前例 {@code TrackingStartNotificationAdapter}）。
 */
class TransportPhaseNotificationAdapterTest {

    private static final BookingId ID = new BookingId(UUID.fromString("00000000-0000-0000-0000-00000039a211"));
    private static final UUID MANAGER = UUID.fromString("00000000-0000-0000-0000-00000039a212");
    private static final UtcInstant COMMITTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));
    private static final UtcInstant OCCURRED_AT = new UtcInstant(Instant.parse("2026-10-20T02:30:00Z"));

    private final InMemoryBookingRepository repository = new InMemoryBookingRepository();
    private final TransportPhaseNotificationAdapter adapter =
            new TransportPhaseNotificationAdapter(new TransportPhaseCommandService(repository));

    private void saved(BookingStatus status) {
        Booking booking = Booking.confirm(ID, allConditions(), terms(), TRACKING_NUMBER, SALES, COMMITTED_AT)
                .booking();
        Booking stored = Booking.reconstitute(
                booking.id(), booking.trackingNumber(), status, TransportPhase.BEFORE_PICKUP, booking.versions(), 0);
        repository.save(stored, SALES, processedCommand(stored));
    }

    private static TransportPhaseNotificationRequest request(int milestoneNo) {
        return new TransportPhaseNotificationRequest(
                ID.value(), TRACKING_NUMBER.value(), milestoneNo, OCCURRED_AT, MANAGER);
    }

    @Test
    void 行8_集荷を記録したら記録したを返し予約は輸送中になる() {
        saved(BookingStatus.CONFIRMED);

        assertThat(adapter.notifyPickedUp(request(1))).isEqualTo(new TransportPhaseNotificationReceipt.Recorded());
        assertThat(repository
                        .findByTrackingNumber(TRACKING_NUMBER)
                        .orElseThrow()
                        .status())
                .isEqualTo(BookingStatus.IN_TRANSIT);
    }

    @Test
    void 行8_引渡しを記録したら記録したを返し2度目は記録済み() {
        saved(BookingStatus.CONFIRMED);

        assertThat(adapter.notifyDelivered(request(2))).isEqualTo(new TransportPhaseNotificationReceipt.Recorded());
        assertThat(adapter.notifyDelivered(request(2)))
                .isEqualTo(new TransportPhaseNotificationReceipt.AlreadyRecorded());
        assertThat(adapter.notifyPickedUp(request(1)))
                .isEqualTo(new TransportPhaseNotificationReceipt.AlreadyRecorded());
    }

    @Test
    void 行8_予約がなければ理由を付けて記録しなかったを返す() {
        assertThat(adapter.notifyPickedUp(request(1)))
                .isEqualTo(new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_NOT_FOUND));
    }

    @Test
    void 行8_取消済みなら理由を付けて記録しなかったを返す() {
        saved(BookingStatus.CANCELLED);

        assertThat(adapter.notifyDelivered(request(2)))
                .isEqualTo(new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_CANCELLED));
    }

    @Test
    void 行8_申請中なら理由を付けて記録しなかったを返す() {
        saved(BookingStatus.CANCELLATION_PENDING);

        assertThat(adapter.notifyPickedUp(request(1)))
                .isEqualTo(new TransportPhaseNotificationReceipt.NotRecorded(
                        TransportPhaseNotificationReceipt.NotRecorded.BOOKING_UNDER_REQUEST));
    }

    @Test
    void 行8_理由は公開APIの定数で予約のドメインの名前と独立() {
        assertThat(TransportPhaseNotificationReceipt.NotRecorded.BOOKING_NOT_FOUND)
                .isEqualTo("BOOKING_NOT_FOUND");
        assertThat(TransportPhaseNotificationReceipt.NotRecorded.BOOKING_CANCELLED)
                .isEqualTo("BOOKING_CANCELLED");
        assertThat(TransportPhaseNotificationReceipt.NotRecorded.BOOKING_UNDER_REQUEST)
                .isEqualTo("BOOKING_UNDER_REQUEST");
    }
}
