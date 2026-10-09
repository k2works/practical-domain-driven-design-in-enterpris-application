package com.example.cargotracker.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.outboundservices.acl.QuotationBookability;
import com.example.cargotracker.booking.domain.model.BookingFixture;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingTerms;
import com.example.cargotracker.booking.domain.model.valueobjects.TrackingNumber;
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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本予約の同時の確定を、別々のトランザクションでの本当の同時実行で確かめる（B-INV-03・B-INV-11、Bolt 24 の H2）。
 * 2 つのトランザクションが「すでに決着した確定か」を確かめた後（見積りの照会の中）に、ラッチでそろえて同時に保存する。
 * READ COMMITTED では、後の INSERT は先の一意制約の行を待ち、先がコミットすると見積り ID の一意制約で失敗する。負けた側は
 * セーブポイントに戻し、同じトランザクションで勝った側の結果を読み直す。見積りの公開 API は差し替え、確定サービスはトランザクションの
 * テンプレートで包む（{@code @Transactional} の代わり）。テストの間でデータを共有しないよう、クラスに {@code @Transactional} を付けず、
 * 見積り ID と業務番号はテストごとに新しくする。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingConfirmationConcurrentIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final Instant NOW = Instant.parse("2083-01-05T03:00:00Z");
    private static final CompanyId A_COMPANY = new CompanyId(UUID.randomUUID());

    @Autowired
    MyBatisBookingRepository repository;

    @Autowired
    MyBatisBookingSagaRepository sagaRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final Random random = new Random();

    @Test
    void 同じコマンドIDで同時に確定すると予約も処理済みコマンドも1件で両方が同じ追跡番号を返す() {
        BookingTerms terms = uniqueTerms();
        CommandId commandId = CommandId.random();
        UUID sales = UUID.randomUUID();

        List<BookingConfirmationOutcome> outcomes =
                confirmConcurrently(terms, List.of(command(commandId, terms, sales), command(commandId, terms, sales)));

        assertThat(outcomes)
                .allSatisfy(outcome -> assertThat(outcome).isInstanceOf(BookingConfirmationOutcome.Confirmed.class));
        assertThat(trackingNumbers(outcomes)).as("負けた側も最初の結果を返す").hasSize(1);
        assertThat(countBookings(terms)).isEqualTo(1);
        assertThat(countProcessedCommands(commandId)).isEqualTo(1);
        assertThat(countVersionsAndSagas(terms)).as("負けた側の予約版と予約サガはセーブポイントで戻る").containsExactly(1, 1);
    }

    @Test
    void 同じコマンドIDで別の見積りを同時に確定すると予約は1件で負けた側は衝突になる() {
        BookingTerms first = uniqueTerms();
        BookingTerms second = uniqueTerms();
        CommandId commandId = CommandId.random();
        UUID sales = UUID.randomUUID();

        List<BookingConfirmationOutcome> outcomes = confirmConcurrently(
                Map.of(first.transportRequestNumber(), first, second.transportRequestNumber(), second),
                List.of(command(commandId, first, sales), command(commandId, second, sales)));

        assertThat(outcomes)
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(
                        BookingConfirmationOutcome.Confirmed.class, BookingConfirmationOutcome.CommandConflict.class);
        assertThat(countBookings(first) + countBookings(second)).isEqualTo(1);
        assertThat(countProcessedCommands(commandId)).isEqualTo(1);
    }

    @Test
    void 別のコマンドIDと別の営業担当者で同時に確定すると予約は1件で負けた側は既存の追跡番号を返す() {
        BookingTerms terms = uniqueTerms();

        List<BookingConfirmationOutcome> outcomes = confirmConcurrently(
                terms,
                List.of(
                        command(CommandId.random(), terms, UUID.randomUUID()),
                        command(CommandId.random(), terms, UUID.randomUUID())));

        assertThat(outcomes)
                .extracting(Object::getClass)
                .containsExactlyInAnyOrder(
                        BookingConfirmationOutcome.Confirmed.class, BookingConfirmationOutcome.AlreadyBooked.class);
        assertThat(trackingNumbers(outcomes)).as("負けた側は勝った側の追跡番号を返す").hasSize(1);
        assertThat(countBookings(terms)).isEqualTo(1);
        assertThat(countVersionsAndSagas(terms)).containsExactly(1, 1);
    }

    /** 2 つの確定を別々のスレッドとトランザクションで、見積りの照会の中でそろえてから進める。 */
    private List<BookingConfirmationOutcome> confirmConcurrently(
            BookingTerms terms, List<ConfirmBookingCommand> commands) {
        return confirmConcurrently(Map.of(terms.transportRequestNumber(), terms), commands);
    }

    /** 業務番号ごとの見積りを返す見積りの公開 API で確定する。 */
    private List<BookingConfirmationOutcome> confirmConcurrently(
            Map<String, BookingTerms> termsByNumber, List<ConfirmBookingCommand> commands) {
        CountDownLatch bothChecked = new CountDownLatch(commands.size());
        BookingCommandService service = new BookingCommandService(
                repository,
                sagaRepository,
                () -> TrackingNumber.generate(random),
                new QuotationBookability(request -> {
                    bothChecked.countDown();
                    awaitQuietly(bothChecked);
                    return bookable(termsByNumber.get(request.transportRequestNumber()));
                }),
                event -> {},
                Clock.fixed(NOW, ZoneOffset.UTC));
        ExecutorService executor = Executors.newFixedThreadPool(commands.size());
        try {
            List<CompletableFuture<BookingConfirmationOutcome>> futures = commands.stream()
                    .map(command -> CompletableFuture.supplyAsync(
                            () -> transactionTemplate.execute(status -> service.confirm(command)), executor))
                    .toList();
            return futures.stream()
                    .map(future ->
                            future.orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).join())
                    .toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<TrackingNumber> trackingNumbers(List<BookingConfirmationOutcome> outcomes) {
        return outcomes.stream()
                .map(outcome -> switch (outcome) {
                    case BookingConfirmationOutcome.Confirmed(TrackingNumber trackingNumber) -> trackingNumber;
                    case BookingConfirmationOutcome.AlreadyBooked(TrackingNumber trackingNumber) -> trackingNumber;
                    default -> throw new AssertionError("追跡番号を返す結果ではない: " + outcome);
                })
                .distinct()
                .toList();
    }

    private int countBookings(BookingTerms terms) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking.booking WHERE quotation_id = ?", Integer.class, terms.quotationId());
    }

    private List<Integer> countVersionsAndSagas(BookingTerms terms) {
        return List.of(
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM booking.booking_version WHERE quotation_id = ?",
                        Integer.class,
                        terms.quotationId()),
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM booking.booking_saga s JOIN booking.booking b ON b.id = s.booking_id"
                                + " WHERE b.quotation_id = ?",
                        Integer.class,
                        terms.quotationId()));
    }

    private int countProcessedCommands(CommandId commandId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM booking.processed_command WHERE command_id = ?",
                Integer.class,
                commandId.value());
    }

    private static ConfirmBookingCommand command(CommandId commandId, BookingTerms terms, UUID sales) {
        return new ConfirmBookingCommand(
                commandId,
                terms.transportRequestNumber(),
                terms.quotationNo(),
                new AuthenticatedActor(new UserId(sales), A_COMPANY, Set.of(Role.SALES), "担当", "A 社"),
                true);
    }

    /** テストごとに新しい見積り ID と業務番号（2083 年。ほかのテストと重ならない）。 */
    private BookingTerms uniqueTerms() {
        BookingTerms base = BookingFixture.terms();
        return new BookingTerms(
                UUID.randomUUID(),
                base.transportRequestVersionNo(),
                "TR-2083-" + (100000 + random.nextInt(900000)),
                UUID.randomUUID(),
                1,
                base.shipperCompanyId(),
                base.consigneeCompanyId(),
                base.routingCaseNumber(),
                base.routeVersionNo(),
                base.cargoCategory(),
                base.cargoSummary(),
                base.shipperApproverId());
    }

    private static BookableQuotationResult bookable(BookingTerms terms) {
        return new BookableQuotationResult.Bookable(
                terms.transportRequestId(),
                terms.transportRequestVersionNo(),
                terms.transportRequestNumber(),
                terms.quotationId(),
                terms.quotationNo(),
                terms.shipperCompanyId(),
                terms.consigneeCompanyId(),
                terms.routingCaseNumber(),
                terms.routeVersionNo(),
                terms.cargoCategory(),
                terms.cargoSummary(),
                terms.shipperApproverId(),
                new UtcInstant(NOW.minusSeconds(3600)),
                new UtcInstant(NOW.plusSeconds(3600)));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("2 つのトランザクションがそろいませんでした");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
