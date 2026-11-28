package com.example.cargotracker.booking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.application.sagas.ConcurrentBookingSagaUpdateException;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

/**
 * 追跡の開始の結果で予約サガを完了にする入力ポート（予約の公開 API の実装が委ねる。ADR-015。Bolt 25）。予約 ID で冪等で、処理中の
 * 予約サガだけを完了にする。予約サガのない予約は、警告のログを残して「見つからない」を返す（再配信で直らない欠け。T-58・T-62）。
 */
class BookingSagaCommandServiceTest {

    private static final UtcInstant STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00Z"));
    private static final UtcInstant TRACKING_STARTED_AT = new UtcInstant(Instant.parse("2026-10-08T09:00:00Z"));

    private final InMemoryBookingSagaRepository sagaRepository = new InMemoryBookingSagaRepository();
    private final BookingSagaCommandService service = new BookingSagaCommandService(sagaRepository);

    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingSagaCommandService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    @Test
    void 処理中の予約サガを完了にする() {
        BookingId bookingId = startedSaga();

        TrackingStartOutcome outcome = service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.COMPLETED);
        BookingSaga saga = sagaRepository.findByBookingId(bookingId).orElseThrow();
        assertThat(saga.status()).isEqualTo(BookingSagaStatus.COMPLETED);
        assertThat(saga.version()).isEqualTo(1);
    }

    @Test
    void 完了済みの予約サガへの同じ結果の再通知は何もしない() {
        BookingId bookingId = startedSaga();
        service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        TrackingStartOutcome outcome = service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.ALREADY_COMPLETED);
        assertThat(sagaRepository.findByBookingId(bookingId).orElseThrow().version())
                .isEqualTo(1);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void 予約サガのない予約は警告のログを残して見つからないを返す() {
        BookingId unknown = new BookingId(UUID.randomUUID());

        TrackingStartOutcome outcome = service.completeTrackingStart(unknown, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.SAGA_NOT_FOUND);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(unknown.value().toString());
        });
    }

    /**
     * 処理中でも完了でもない予約サガ（失敗・有人確認要。W8）に遅れて届いた結果は、例外にせず警告のログを残して「処理中でない」を返す。
     * 例外にすると、再配信しても直らない失敗が配信の記録に残り続ける（Bolt 25 レビュー P-3・A-1。T-57・T-62）。
     */
    @ParameterizedTest
    @EnumSource(
            value = BookingSagaStatus.class,
            names = {"FAILED", "NEEDS_HUMAN"})
    void 処理中でも完了でもない予約サガは完了にせず警告のログを残す(BookingSagaStatus status) {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        BookingSaga started = BookingSaga.start(bookingId, BookingFixture.TRACKING_NUMBER, STARTED_AT);
        sagaRepository.save(BookingSaga.reconstitute(
                started.id(),
                bookingId,
                started.trackingNumber(),
                status,
                started.currentStep(),
                started.startedAt(),
                started.version()));

        TrackingStartOutcome outcome = service.completeTrackingStart(bookingId, TRACKING_STARTED_AT);

        assertThat(outcome).isEqualTo(TrackingStartOutcome.SAGA_NOT_IN_PROGRESS);
        assertThat(sagaRepository.findByBookingId(bookingId).orElseThrow().status())
                .isEqualTo(status);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains(bookingId.value().toString())
                    .contains(status.name());
        });
    }

    /** 同時の通知の負けた側は、楽観ロックの競合の例外のまま戻り、再配信に任せる（Bolt 25 レビュー P-5）。 */
    @Test
    void 読んだ後にほかの通知が先に完了にしていたら楽観ロックの競合の例外のまま戻す() {
        BookingId bookingId = startedSaga();
        BookingSagaCommandService raced = new BookingSagaCommandService(new BookingSagaRepository() {
            @Override
            public void save(BookingSaga saga) {
                sagaRepository.save(saga);
            }

            @Override
            public Optional<BookingSaga> findByBookingId(BookingId id) {
                Optional<BookingSaga> found = sagaRepository.findByBookingId(id);
                // 読んだ直後に、ほかの通知が先に完了にする
                found.ifPresent(saga -> sagaRepository.update(saga.complete()));
                return found;
            }

            @Override
            public void update(BookingSaga saga) {
                sagaRepository.update(saga);
            }

            @Override
            public Map<BookingId, BookingSagaStatus> findStatusesByBookingIds(Set<BookingId> bookingIds) {
                return sagaRepository.findStatusesByBookingIds(bookingIds);
            }
        });

        assertThatThrownBy(() -> raced.completeTrackingStart(bookingId, TRACKING_STARTED_AT))
                .isInstanceOf(ConcurrentBookingSagaUpdateException.class);
    }

    private BookingId startedSaga() {
        BookingId bookingId = new BookingId(UUID.randomUUID());
        sagaRepository.save(BookingSaga.start(bookingId, BookingFixture.TRACKING_NUMBER, STARTED_AT));
        return bookingId;
    }
}
