package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingConfirmation;
import com.example.cargotracker.booking.domain.model.aggregates.BookingConfirmationRejected;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.aggregates.TrackingNumberIssuer;
import com.example.cargotracker.booking.domain.model.sagas.BookingSaga;
import com.example.cargotracker.booking.domain.model.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingConditions;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本予約の確定の入力ポート（営業担当者が使う。US-04 AC1・AC2。Bolt 23）。トランザクションの境界になる。
 * commit 時刻は、このトランザクションの中で保存の前に Clock から取り、同じ値で見積りの公開 API に照会し、予約版に記録する（ADR-016）。
 * 確定したら予約サガを処理中で始め、DE-07 を発行する（追跡の開始は追跡が購読する。ADR-015）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingCommandService {

    private final BookingRepository repository;
    private final BookingSagaRepository sagaRepository;
    private final TrackingNumberIssuer trackingNumberIssuer;
    private final QuotationBookability quotations;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BookingCommandService(
            BookingRepository repository,
            BookingSagaRepository sagaRepository,
            TrackingNumberIssuer trackingNumberIssuer,
            QuotationBookability quotations,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.sagaRepository = sagaRepository;
        this.trackingNumberIssuer = trackingNumberIssuer;
        this.quotations = quotations;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 本予約を確定する（US-04 AC1・AC2、BR-01、BR-10、B-INV-01・10・11）。 */
    @Transactional
    public BookingConfirmationOutcome confirm(ConfirmBookingCommand command) {
        if (!command.operator().hasRole(Role.SALES)) {
            return new BookingConfirmationOutcome.Forbidden();
        }
        UtcInstant committedAt = new UtcInstant(clock.instant());
        BookingTerms terms;
        switch (quotations.check(command.quotationId(), committedAt)) {
            case QuotationBookability.Result.Unavailable(QuotationUnavailability reason) -> {
                return reason == QuotationUnavailability.EXPIRED
                        ? new BookingConfirmationOutcome.Expired()
                        : new BookingConfirmationOutcome.QuotationUnavailable(reason);
            }
            case QuotationBookability.Result.Bookable(BookingTerms bookable) -> terms = bookable;
        }
        // 有効な見積り・荷主の承認・承認済み経路版は、見積りが確定に使えると判定した時点でそろっている（ADR-016）
        BookingConditions conditions =
                new BookingConditions(true, !terms.cargoSummary().isBlank(), true, true, command.staffConfirmed());
        // 追跡番号は発行のたびに DB を照会するため、条件が欠けていれば発行の前に返す（Bolt 23 レビュー L-1）
        if (!conditions.missing().isEmpty()) {
            return new BookingConfirmationOutcome.MissingConditions(conditions.missing());
        }
        UUID operator = command.operator().userId().value();
        TrackingNumber trackingNumber = trackingNumberIssuer.issue();
        BookingConfirmation confirmation;
        try {
            confirmation = Booking.confirm(
                    new BookingId(UUID.randomUUID()), conditions, terms, trackingNumber, operator, committedAt);
        } catch (BookingConfirmationRejected rejected) {
            return new BookingConfirmationOutcome.MissingConditions(rejected.missingConditions());
        }
        try {
            repository.save(confirmation.booking(), operator);
        } catch (DuplicateBookingException _) {
            return new BookingConfirmationOutcome.AlreadyBooked();
        }
        sagaRepository.save(BookingSaga.start(confirmation.booking().id(), trackingNumber, committedAt));
        eventPublisher.publishEvent(confirmation.event());
        return new BookingConfirmationOutcome.Confirmed(trackingNumber);
    }
}
