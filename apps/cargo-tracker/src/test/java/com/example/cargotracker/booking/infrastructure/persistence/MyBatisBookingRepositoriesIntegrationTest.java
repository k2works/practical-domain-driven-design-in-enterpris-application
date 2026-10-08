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
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 貨物予約と予約サガのリポジトリ（PostgreSQL。Bolt 23）。 */
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

        repository.save(booking, BookingFixture.SALES);

        Booking found =
                repository.findByTrackingNumber(booking.trackingNumber()).orElseThrow();
        assertThat(found.id()).isEqualTo(booking.id());
        assertThat(found.status()).isEqualTo(booking.status());
        assertThat(found.transportPhase()).isEqualTo(booking.transportPhase());
        assertThat(found.versions()).isEqualTo(booking.versions());
        assertThat(found.transportRequestNumber()).isEqualTo("TR-2026-0001");
        assertThat(repository.existsByTrackingNumber(booking.trackingNumber())).isTrue();
        assertThat(repository.existsByTrackingNumber(TrackingNumber.generate(random)))
                .isFalse();
    }

    @Test
    void 同じ見積りの二件目の予約はドメインの例外になる() {
        UUID quotationId = UUID.randomUUID();
        repository.save(confirmed(terms(quotationId)), BookingFixture.SALES);

        Booking second = confirmed(terms(quotationId));
        assertThatThrownBy(() -> repository.save(second, BookingFixture.SALES))
                .isInstanceOf(DuplicateBookingException.class);
        assertThat(repository.findByTrackingNumber(second.trackingNumber())).isEmpty();
    }

    @Test
    void 追跡番号の重なりは同じ見積りの予約と区別して技術の失敗にする() {
        Booking first = confirmed(terms(UUID.randomUUID()));
        repository.save(first, BookingFixture.SALES);

        Booking collided = confirmed(terms(UUID.randomUUID()), first.trackingNumber());
        assertThatThrownBy(() -> repository.save(collided, BookingFixture.SALES))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(DuplicateBookingException.class);
    }

    @Test
    void 予約サガを処理中で保存し予約IDで読み出す() {
        Booking booking = confirmed(terms(UUID.randomUUID()));
        repository.save(booking, BookingFixture.SALES);
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

    private static BookingTerms terms(UUID quotationId) {
        BookingTerms base = BookingFixture.terms();
        return new BookingTerms(
                base.transportRequestId(),
                base.transportRequestVersionNo(),
                base.transportRequestNumber(),
                quotationId,
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
    }
}
