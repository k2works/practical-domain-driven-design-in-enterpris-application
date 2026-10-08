package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 予約の照会（S-09 本予約の確定、S-24 予約の詳細。Bolt 23b）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingQueryService {

    private final BookingRepository repository;
    private final BookingSagaRepository sagaRepository;
    private final QuotationBookability quotations;
    private final Clock clock;

    public BookingQueryService(
            BookingRepository repository,
            BookingSagaRepository sagaRepository,
            QuotationBookability quotations,
            Clock clock) {
        this.repository = repository;
        this.sagaRepository = sagaRepository;
        this.quotations = quotations;
        this.clock = clock;
    }

    /**
     * 本予約の確定の画面を開いたときの判定。開いた時刻で見積りの公開 API に照会する参考の判定で、確定の可否は送ったときの照会
     * （commit 時刻）で決まる（ADR-016）。確定済みは見積りの公開 API では分からないので、予約の側で見積り ID から確かめる。
     */
    @Transactional(readOnly = true)
    public BookingConfirmationPage confirmation(String transportRequestNumber, int quotationNo) {
        UtcInstant now = new UtcInstant(clock.instant());
        return switch (quotations.check(transportRequestNumber, quotationNo, now)) {
            case QuotationBookability.Result.Unavailable(QuotationUnavailability reason) ->
                new BookingConfirmationPage.Unavailable(reason);
            case QuotationBookability.Result.Bookable(
                    BookingTerms terms,
                    UtcInstant shipperApprovedAt,
                    UtcInstant expiresAt) ->
                repository.existsByQuotationId(terms.quotationId())
                        ? new BookingConfirmationPage.AlreadyBooked()
                        : new BookingConfirmationPage.Available(terms, shipperApprovedAt, expiresAt);
        };
    }

    /** 予約の詳細。予約と予約サガの状態を返す。予約があれば予約サガは同じトランザクションで作られている（ADR-015）。 */
    @Transactional(readOnly = true)
    public Optional<BookingDetail> detail(TrackingNumber trackingNumber) {
        return repository
                .findByTrackingNumber(trackingNumber)
                .map(booking -> new BookingDetail(
                        booking,
                        sagaRepository
                                .findByBookingId(booking.id())
                                .orElseThrow(() -> new IllegalStateException(
                                        "貨物予約に予約サガがない（本予約の確定と同じトランザクションで作る。ADR-015）: " + booking.id()))
                                .status()));
    }
}
