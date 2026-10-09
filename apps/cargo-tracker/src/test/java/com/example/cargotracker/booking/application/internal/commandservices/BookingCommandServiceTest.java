package com.example.cargotracker.booking.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.acceptance.InMemoryBookingRepository;
import com.example.cargotracker.booking.acceptance.InMemoryBookingSagaRepository;
import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationUnavailability;
import com.example.cargotracker.booking.application.sagas.BookingSagaStatus;
import com.example.cargotracker.booking.domain.events.BookingConfirmed;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.DuplicateBookingException;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingCondition;
import com.example.cargotracker.booking.domain.model.valueobjects.ProcessedCommand;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.quotation.api.BookableQuotationRequest;
import com.example.cargotracker.quotation.api.BookableQuotationResult;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 本予約の確定の入力ポート（US-04 AC1・AC2・AC4。Bolt 23 レビュー L-1・L-3、Bolt 24）。見積りの公開 API と追跡番号の発行を
 * 差し替えて、受入テストで通らない分岐（見つからない・置換済み・貨物の要約が空・権限の先行・判定の順序・同時の確定の負け）を確かめる。
 */
class BookingCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:00:00.123456Z");
    private static final CompanyId A_COMPANY = new CompanyId(UUID.randomUUID());

    private final InMemoryBookingRepository repository = new InMemoryBookingRepository();
    private final InMemoryBookingSagaRepository sagaRepository = new InMemoryBookingSagaRepository();
    private final AtomicInteger issued = new AtomicInteger();
    private final List<BookableQuotationRequest> queries = new ArrayList<>();
    private final List<Object> published = new ArrayList<>();
    private BookableQuotationResult quotation = bookable(BookingFixture.terms().cargoSummary());
    private final Logger logger = (Logger) LoggerFactory.getLogger(BookingCommandService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private final BookingCommandService service = serviceWith(repository);

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private BookingCommandService serviceWith(InMemoryBookingRepository bookingRepository) {
        return new BookingCommandService(
                bookingRepository,
                sagaRepository,
                () -> {
                    issued.incrementAndGet();
                    return BookingFixture.TRACKING_NUMBER;
                },
                new QuotationBookability(request -> {
                    queries.add(request);
                    return quotation;
                }),
                published::add,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 確定すると照会と同じcommit時刻で予約と予約サガを保存しDE07を発行する() {
        BookingConfirmationOutcome outcome = service.confirm(command(Role.SALES, true));

        assertThat(outcome).isEqualTo(new BookingConfirmationOutcome.Confirmed(BookingFixture.TRACKING_NUMBER));
        assertThat(queries)
                .as("業務番号と見積り番号で、commit 時刻を渡して照会する（ADR-016、D-4）")
                .containsExactly(new BookableQuotationRequest("TR-2026-0001", 1, new UtcInstant(NOW)));
        Booking booking = repository.findAll().getFirst();
        assertThat(booking.currentVersion().committedAt()).isEqualTo(new UtcInstant(NOW));
        assertThat(sagaRepository.findByBookingId(booking.id()).orElseThrow().status())
                .isEqualTo(BookingSagaStatus.IN_PROGRESS);
        assertThat(published).singleElement().isInstanceOf(BookingConfirmed.class);
    }

    @Test
    void 見つからない見積りと置換済みの見積りは理由で返し追跡番号を発行しない() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.QUOTATION_NOT_FOUND);
        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability.NOT_FOUND));

        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.REPLACED);
        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.QuotationUnavailable(QuotationUnavailability.REPLACED));

        assertThat(issued).hasValue(0);
        assertThat(repository.findAll()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void 貨物の要約が空なら必須の貨物情報を不足条件で返し追跡番号を発行しない() {
        quotation = bookable(" ");

        assertThat(service.confirm(command(Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.MissingConditions(List.of(BookingCondition.REQUIRED_CARGO)));
        assertThat(issued).hasValue(0);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void 営業担当者でなければ見積りを照会せずに拒否する() {
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirm(command(Role.SHIPPER, false))).isEqualTo(new BookingConfirmationOutcome.Forbidden());
        assertThat(queries).isEmpty();
        assertThat(issued).hasValue(0);
    }

    @Test
    void 同じコマンドIDの再送は見積りを照会せず追跡番号を発行せず最初の追跡番号を返す() {
        CommandId commandId = CommandId.random();
        service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, true));
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, true)))
                .as("確定の後に見積りが失効しても、再送には最初の結果を返す（H1）")
                .isEqualTo(new BookingConfirmationOutcome.Confirmed(BookingFixture.TRACKING_NUMBER));
        assertThat(queries).hasSize(1);
        assertThat(issued).hasValue(1);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(published).hasSize(1);
    }

    @Test
    void 同じコマンドIDで内容が違えば衝突として拒否し警告のログを残す() {
        CommandId commandId = CommandId.random();
        service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, true));

        assertThat(service.confirm(command(commandId, UUID.randomUUID(), Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.CommandConflict());
        assertThat(queries).hasSize(1);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains(commandId.value().toString());
        });
    }

    @Test
    void 同じ見積りの予約があれば見積りを照会せず追跡番号を発行せず既存の追跡番号を返す() {
        service.confirm(command(CommandId.random(), BookingFixture.SALES, Role.SALES, true));
        quotation = new BookableQuotationResult.NotBookable(BookableQuotationResult.NotBookable.EXPIRED);

        assertThat(service.confirm(command(CommandId.random(), UUID.randomUUID(), Role.SALES, true)))
                .as("確定の後に見積りが失効しても、同じ見積りの予約があれば既存の追跡番号を返す（H1）")
                .isEqualTo(new BookingConfirmationOutcome.AlreadyBooked(BookingFixture.TRACKING_NUMBER));
        assertThat(queries).hasSize(1);
        assertThat(issued).hasValue(1);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void 拒否した要求は処理済みコマンドに記録せず同じコマンドIDで直して送り直せる() {
        CommandId commandId = CommandId.random();
        assertThat(service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, false)))
                .isInstanceOf(BookingConfirmationOutcome.MissingConditions.class);

        assertThat(service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.Confirmed(BookingFixture.TRACKING_NUMBER));
        assertThat(repository.findProcessedCommand(commandId)).isPresent();
    }

    @Test
    void 確定すると処理済みコマンドを予約と一緒に記録する() {
        CommandId commandId = CommandId.random();
        service.confirm(command(commandId, BookingFixture.SALES, Role.SALES, true));

        assertThat(repository.findProcessedCommand(commandId)).hasValueSatisfying(processed -> {
            assertThat(processed.trackingNumber()).isEqualTo(BookingFixture.TRACKING_NUMBER);
            assertThat(processed.bookingId())
                    .isEqualTo(repository.findAll().getFirst().id());
            assertThat(processed.processedAt()).isEqualTo(new UtcInstant(NOW));
            assertThat(processed.sameContentAs(ProcessedCommand.confirmBookingPayloadHash(
                            "TR-2026-0001", 1, new UserId(BookingFixture.SALES))))
                    .isTrue();
        });
    }

    @Test
    void 同時の確定で同じ見積りの一意制約に負けたら読み直して既存の追跡番号を返す() {
        TrackingNumber winner = new TrackingNumber("CTWNNERABCDEFG");
        InMemoryBookingRepository racing = new InMemoryBookingRepository() {
            private boolean firstLookup = true;

            @Override
            public synchronized Optional<TrackingNumber> findTrackingNumber(
                    String transportRequestNumber, int quotationNo) {
                // 1 回目の照会の後に、別の確定が先に保存した状態を作る
                if (firstLookup) {
                    firstLookup = false;
                    return Optional.empty();
                }
                return Optional.of(winner);
            }

            @Override
            public synchronized void save(Booking booking, UUID operator, ProcessedCommand processedCommand) {
                throw new DuplicateBookingException("同じ見積りの予約がすでにある", null);
            }
        };

        assertThat(serviceWith(racing).confirm(command(CommandId.random(), BookingFixture.SALES, Role.SALES, true)))
                .isEqualTo(new BookingConfirmationOutcome.AlreadyBooked(winner));
        assertThat(published).isEmpty();
    }

    private static ConfirmBookingCommand command(Role role, boolean staffConfirmed) {
        return command(CommandId.random(), BookingFixture.SALES, role, staffConfirmed);
    }

    private static ConfirmBookingCommand command(
            CommandId commandId, UUID operator, Role role, boolean staffConfirmed) {
        return new ConfirmBookingCommand(
                commandId,
                "TR-2026-0001",
                1,
                new AuthenticatedActor(new UserId(operator), A_COMPANY, Set.of(role), "担当", "A 社"),
                staffConfirmed);
    }

    private static BookableQuotationResult bookable(String cargoSummary) {
        var terms = BookingFixture.terms();
        return new BookableQuotationResult.Bookable(
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.transportRequestNumber(),
                terms.quotationId(),
                1,
                terms.shipperCompanyId(),
                terms.consigneeCompanyId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                terms.cargoCategory(),
                cargoSummary,
                terms.shipperApproverId(),
                new UtcInstant(NOW.minusSeconds(3600)),
                new UtcInstant(NOW.plusSeconds(3600)));
    }
}
