package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingSummary;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 追跡番号の発行（BR-07。Bolt 23）。使われていない番号が出るまで引き直し、上限は 3 回。 */
class RandomTrackingNumberIssuerTest {

    @Test
    void 使われていない番号を返す() {
        TrackingNumber issued = new RandomTrackingNumberIssuer(new Existing(Set.of()), new Random(1)).issue();

        assertThat(issued.value()).startsWith("CT").hasSize(14);
    }

    @Test
    void 使われている番号なら引き直す() {
        TrackingNumber first = TrackingNumber.generate(new Random(7));
        Existing existing = new Existing(Set.of(first.value()));

        TrackingNumber issued = new RandomTrackingNumberIssuer(existing, new Random(7)).issue();

        assertThat(issued).isNotEqualTo(first);
        assertThat(existing.asked.get()).isEqualTo(2);
    }

    @Test
    void 三回引いても使われていない番号がなければ失敗する() {
        Existing allUsed = new Existing(null);

        RandomTrackingNumberIssuer issuer = new RandomTrackingNumberIssuer(allUsed, new Random(3));

        assertThatThrownBy(issuer::issue).isInstanceOf(IllegalStateException.class);
        assertThat(allUsed.asked.get()).isEqualTo(RandomTrackingNumberIssuer.MAX_ATTEMPTS);
    }

    /** 使われている追跡番号の集合（null ならすべて使われている）。 */
    private static final class Existing implements BookingRepository {

        private final Set<String> used;
        private final AtomicInteger asked = new AtomicInteger();

        Existing(Set<String> used) {
            this.used = used;
        }

        @Override
        public void save(Booking booking, UUID operator, ProcessedCommand processedCommand) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Booking> findByTrackingNumber(TrackingNumber trackingNumber) {
            return Optional.empty();
        }

        @Override
        public boolean existsByTrackingNumber(TrackingNumber trackingNumber) {
            asked.incrementAndGet();
            return used == null || used.contains(trackingNumber.value());
        }

        @Override
        public Optional<ProcessedCommand> findProcessedCommand(CommandId commandId) {
            return Optional.empty();
        }

        @Override
        public List<BookingSummary> findRecentSummaries(int limit) {
            return List.of();
        }

        @Override
        public List<BookingSummary> findRecentSummariesByShipper(CompanyId shipperCompanyId, int limit) {
            return List.of();
        }

        @Override
        public Optional<TrackingNumber> findTrackingNumber(String transportRequestNumber, int quotationNo) {
            return Optional.empty();
        }
    }
}
