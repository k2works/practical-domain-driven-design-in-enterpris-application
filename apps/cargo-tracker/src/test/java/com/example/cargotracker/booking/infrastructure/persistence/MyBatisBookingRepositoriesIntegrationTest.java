package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 貨物予約と予約サガのリポジトリ（PostgreSQL。Bolt 23）。処理済みコマンドと業務番号・見積り番号での照会（Bolt 24）。 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MyBatisBookingRepositoriesIntegrationTest {

    private static final UtcInstant COMMITTED_AT = new UtcInstant(Instant.parse("2026-10-08T08:59:00.123456Z"));

    @Autowired
    MyBatisBookingRepository repository;

    @Autowired
    MyBatisBookingSagaRepository sagaRepository;

    private final Random random = new Random();

    @Test
    void 確定した貨物予約を予約版とあわせて保存し追跡番号で読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));

        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));

        Booking found =
                repository.findByTrackingNumber(booking.trackingNumber()).orElseThrow();
        assertThat(found.id()).isEqualTo(booking.id());
        assertThat(found.status()).isEqualTo(booking.status());
        assertThat(found.transportPhase()).isEqualTo(booking.transportPhase());
        assertThat(found.versions()).isEqualTo(booking.versions());
        assertThat(found.transportRequestNumber()).isEqualTo(booking.transportRequestNumber());
        assertThat(repository.existsByTrackingNumber(booking.trackingNumber())).isTrue();
        assertThat(repository.existsByTrackingNumber(TrackingNumber.generate(random)))
                .isFalse();
        assertThat(found.quotationNo()).isEqualTo(booking.quotationNo());
        assertThat(repository.findTrackingNumber(booking.transportRequestNumber(), booking.quotationNo()))
                .contains(booking.trackingNumber());
        assertThat(repository.findTrackingNumber(booking.transportRequestNumber(), booking.quotationNo() + 1))
                .isEmpty();
    }

    @Test
    void 処理済みコマンドを貨物予約と一緒に保存しコマンドIDで読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand processed = BookingFixture.processedCommand(booking);

        repository.save(booking, BookingFixture.SALES, processed);

        assertThat(repository.findProcessedCommand(processed.commandId())).contains(processed);
        assertThat(repository.findProcessedCommand(CommandId.random())).isEmpty();
    }

    @Test
    void 同じ見積りの二件目では処理済みコマンドも記録せずトランザクションは続けて使える() {
        UUID quotationId = UUID.randomUUID();
        Booking first = confirmed(terms(quotationId));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));
        Booking second = confirmed(terms(quotationId, first.transportRequestNumber()));
        ProcessedCommand secondCommand = BookingFixture.processedCommand(second);

        assertThatThrownBy(() -> repository.save(second, BookingFixture.SALES, secondCommand))
                .isInstanceOf(DuplicateBookingException.class);

        assertThat(repository.findProcessedCommand(secondCommand.commandId()))
                .as("セーブポイントに戻り、同じトランザクションで読み直せる（Bolt 24、H2）")
                .isEmpty();
        assertThat(repository.findTrackingNumber(first.transportRequestNumber(), first.quotationNo()))
                .contains(first.trackingNumber());
    }

    @Test
    void 処理済みコマンドのコマンドIDの重なりは同じ見積りの予約と区別して技術の失敗にする() {
        Booking first = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand processed = BookingFixture.processedCommand(first);
        repository.save(first, BookingFixture.SALES, processed);

        Booking other = confirmed(terms(UUID.randomUUID()));
        ProcessedCommand sameCommandId = ProcessedCommand.confirmBooking(
                processed.commandId(),
                other.transportRequestNumber(),
                other.quotationNo(),
                new UserId(BookingFixture.SALES),
                other.id(),
                other.trackingNumber(),
                COMMITTED_AT);
        assertThatThrownBy(() -> repository.save(other, BookingFixture.SALES, sameCommandId))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void 同じ見積りの二件目の予約はドメインの例外になる() {
        UUID quotationId = UUID.randomUUID();
        Booking first = confirmed(terms(quotationId));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));

        Booking second = confirmed(terms(quotationId));
        assertThatThrownBy(() -> repository.save(second, BookingFixture.SALES, BookingFixture.processedCommand(second)))
                .isInstanceOf(DuplicateBookingException.class);
        assertThat(repository.findByTrackingNumber(second.trackingNumber())).isEmpty();
    }

    @Test
    void 追跡番号の重なりは同じ見積りの予約と区別して技術の失敗にする() {
        Booking first = confirmed(terms(UUID.randomUUID()));
        repository.save(first, BookingFixture.SALES, BookingFixture.processedCommand(first));

        Booking collided = confirmed(terms(UUID.randomUUID()), first.trackingNumber());
        assertThatThrownBy(() ->
                        repository.save(collided, BookingFixture.SALES, BookingFixture.processedCommand(collided)))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void 予約サガを処理中で保存し予約IDで読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        repository.save(booking, BookingFixture.SALES, BookingFixture.processedCommand(booking));
        BookingSaga saga = BookingSaga.start(booking.id(), booking.trackingNumber(), COMMITTED_AT);

        sagaRepository.save(saga);

        BookingSaga found = sagaRepository.findByBookingId(booking.id()).orElseThrow();
        assertThat(found.id()).isEqualTo(saga.id());
        assertThat(found.status()).isEqualTo(BookingSagaStatus.IN_PROGRESS);
        assertThat(found.trackingNumber()).isEqualTo(booking.trackingNumber());
        assertThat(found.startedAt()).isEqualTo(COMMITTED_AT);
    }

    private Booking confirmed(BookingTerms terms) {
        return confirmed(terms, TrackingNumber.generate(random));
    }

    private static Booking confirmed(BookingTerms terms, TrackingNumber trackingNumber) {
        return Booking.confirm(
                        new BookingId(UUID.randomUUID()),
                        BookingFixture.allConditions(),
                        terms,
                        trackingNumber,
                        BookingFixture.SALES,
                        COMMITTED_AT)
                .booking();
    }

    /** 見積り ID ごとに新しい業務番号（業務番号と見積り番号の一意制約に当たらないように。2082 年はほかのテストと重ならない）。 */
    private BookingTerms terms(UUID quotationId) {
        return terms(quotationId, "TR-2082-" + (100000 + random.nextInt(900000)));
    }

    private static BookingTerms terms(UUID quotationId, String transportRequestNumber) {
        BookingTerms base = BookingFixture.terms();
        return new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                transportRequestNumber,
                quotationId,
                base.quotationNo(),
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
    }
}
