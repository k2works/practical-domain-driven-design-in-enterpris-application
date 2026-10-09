package com.example.cargotracker.booking.application.internal.commandservices;

import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSaga;
import com.example.cargotracker.booking.application.sagas.BookingSagaRepository;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingConfirmation;
import com.example.cargotracker.booking.domain.model.aggregates.BookingConfirmationRejected;
import com.example.cargotracker.booking.domain.model.aggregates.BookingRepository;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.aggregates.TrackingNumberIssuer;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingConditions;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本予約の確定の入力ポート（営業担当者が使う。US-04 AC1・AC2・AC4。Bolt 23・24）。トランザクションの境界になる。
 * commit 時刻は、このトランザクションの中で保存の前に Clock から取り、同じ値で見積りの公開 API に照会し、予約版に記録する（ADR-016）。
 * 確定したら予約サガを処理中で始め、DE-07 を発行する（追跡の開始は追跡が購読する。ADR-015）。
 *
 * <p>判定の順序は、役割 → 処理済みコマンド（B-INV-03）→ 同じ見積りの予約（B-INV-11）→ 見積りの照会 → 確定条件。確定済みの判定を
 * 見積りの照会より前に置くので、確定の後に見積りが失効・置換されても、再送と同じ見積りの確定は失効にしない（Bolt 24）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code BookingConfiguration} が担う。
 */
@Service
public class BookingCommandService {

    private static final Logger LOG = LoggerFactory.getLogger(BookingCommandService.class);

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

    /** 本予約を確定する（US-04 AC1・AC2・AC4、BR-01、BR-10、B-INV-01・03・10・11）。 */
    @Transactional
    public BookingConfirmationOutcome confirm(ConfirmBookingCommand command) {
        if (!command.operator().hasRole(Role.SALES)) {
            return new BookingConfirmationOutcome.Forbidden();
        }
        Optional<BookingConfirmationOutcome> settled = alreadySettled(command);
        if (settled.isPresent()) {
            return settled.get();
        }
        UtcInstant committedAt = new UtcInstant(clock.instant());
        BookingTerms terms;
        switch (quotations.check(command.transportRequestNumber(), command.quotationNo(), committedAt)) {
            case QuotationBookability.Result.Unavailable(QuotationUnavailability reason) -> {
                return reason == QuotationUnavailability.EXPIRED
                        ? new BookingConfirmationOutcome.Expired()
                        : new BookingConfirmationOutcome.QuotationUnavailable(reason);
            }
            case QuotationBookability.Result.Bookable bookable -> terms = bookable.terms();
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
        BookingId bookingId = new BookingId(UUID.randomUUID());
        try {
            confirmation = Booking.confirm(bookingId, conditions, terms, trackingNumber, operator, committedAt);
        } catch (BookingConfirmationRejected rejected) {
            return new BookingConfirmationOutcome.MissingConditions(rejected.missingConditions());
        }
        try {
            repository.save(
                    confirmation.booking(),
                    operator,
                    ProcessedCommand.confirmBooking(
                            command.commandId(),
                            command.transportRequestNumber(),
                            command.quotationNo(),
                            command.operator().userId(),
                            bookingId,
                            trackingNumber,
                            committedAt));
        } catch (DuplicateBookingException e) {
            // 同時の確定に負けた。保存はセーブポイントに戻っているので、同じトランザクションで勝った側の結果を読み直す（Bolt 24 の仮説 H2）
            LOG.info(
                    "同時の本予約の確定に負けたので、勝った側の結果を読み直す: {} 見積 {} commandId={}",
                    command.transportRequestNumber(),
                    command.quotationNo(),
                    command.commandId().value());
            return alreadySettled(command)
                    .orElseThrow(() -> new IllegalStateException(
                            "同じ見積りの一意制約に違反したのに、同じ見積りの予約が見つからない: " + command.transportRequestNumber() + " 見積 "
                                    + command.quotationNo(),
                            e));
        }
        sagaRepository.save(BookingSaga.start(confirmation.booking().id(), trackingNumber, committedAt));
        eventPublisher.publishEvent(confirmation.event());
        return new BookingConfirmationOutcome.Confirmed(trackingNumber);
    }

    /**
     * すでに決着した確定か。同じコマンド ID の処理済みコマンドがあれば、内容が同じなら最初の結果を、違えば衝突を返す（B-INV-03）。
     * なければ、業務番号と見積り番号で同じ見積りの予約を引き、あれば既存の追跡番号を返す（B-INV-11）。
     */
    private Optional<BookingConfirmationOutcome> alreadySettled(ConfirmBookingCommand command) {
        Optional<ProcessedCommand> processed = repository.findProcessedCommand(command.commandId());
        if (processed.isPresent()) {
            if (processed
                    .get()
                    .isSameConfirmation(
                            command.transportRequestNumber(),
                            command.quotationNo(),
                            command.operator().userId())) {
                return Optional.of(
                        new BookingConfirmationOutcome.Confirmed(processed.get().trackingNumber()));
            }
            LOG.warn(
                    "同じコマンド ID で内容の違う本予約の確定を拒否した（衝突。B-INV-03）: {} 見積 {} commandId={} operator={}",
                    command.transportRequestNumber(),
                    command.quotationNo(),
                    command.commandId().value(),
                    command.operator().userId().value());
            return Optional.of(new BookingConfirmationOutcome.CommandConflict());
        }
        return repository
                .findTrackingNumber(command.transportRequestNumber(), command.quotationNo())
                .map(BookingConfirmationOutcome.AlreadyBooked::new);
    }
}
