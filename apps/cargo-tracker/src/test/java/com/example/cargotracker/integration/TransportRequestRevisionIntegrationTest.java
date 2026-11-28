package com.example.cargotracker.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.TestcontainersConfiguration;
import com.example.cargotracker.booking.application.internal.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.application.internal.commandservices.BookingCommandService;
import com.example.cargotracker.booking.application.internal.commandservices.BookingConfirmationOutcome;
import com.example.cargotracker.booking.application.internal.eventhandlers.BookingConfirmedEventHandler;
import com.example.cargotracker.quotation.application.internal.commands.ApproveQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.CalculateQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.PresentQuotationCommand;
import com.example.cargotracker.quotation.application.internal.commands.RequestRouteDesignCommand;
import com.example.cargotracker.quotation.application.internal.commands.ReviseTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commandservices.CalculationOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.QuotationResponseService;
import com.example.cargotracker.quotation.application.internal.commandservices.RevisionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.RouteAssignmentService;
import com.example.cargotracker.quotation.application.internal.commandservices.SubmissionOutcome;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestCommandService;
import com.example.cargotracker.quotation.application.internal.commandservices.TransportRequestReviewService;
import com.example.cargotracker.quotation.domain.model.aggregates.Quotation;
import com.example.cargotracker.quotation.domain.model.aggregates.QuotationRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.QuotationStatus;
import com.example.cargotracker.quotation.domain.model.valueobjects.RevisionRejection;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.CommandId;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 本予約前の条件の変更を、PostgreSQL と本物のサービスで確かめる（US-05 AC1。Bolt 62）。旧版の見積りを無効にすると、輸送要求ごとに有効な
 * 見積りを 1 件に限る部分一意インデックス（Q-INV-18。H2 にはない）の下で、新しい版の見積りを作れる。
 *
 * <p>本予約の確定の知らせ（DE-07）は非同期に届く。届かない間も守れることを確かめるため、DE-07 の受け手を差し替えて何もしない。
 * 本予約の確定は、同じトランザクションで輸送要求を予約確定済みにする（ADR-014 の例外。2026-11-28 の承認）。
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransportRequestRevisionIntegrationTest {

    private static final long TIMEOUT_SECONDS = 30;
    private static final CompanyId SHIPPER = new CompanyId(UUID.randomUUID());
    private static final UserId SHIPPER_USER = new UserId(UUID.randomUUID());
    private static final AuthenticatedActor SALES = new AuthenticatedActor(
            new UserId(UUID.randomUUID()),
            new CompanyId(UUID.randomUUID()),
            Set.of(Role.SALES),
            "担当",
            "A 社",
            Set.of(SHIPPER));

    @MockitoBean
    BookingConfirmedEventHandler delayedBookingNotification;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    TransportRequestCommandService transportRequests;

    @Autowired
    TransportRequestReviewService reviews;

    @Autowired
    QuotationCommandService quotations;

    @Autowired
    QuotationResponseService responses;

    @Autowired
    RouteAssignmentService routes;

    @Autowired
    BookingCommandService bookings;

    @Autowired
    TransportRequestRepository transportRequestRepository;

    @Autowired
    QuotationRepository quotationRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void 変更で旧版の見積りが無効になり有効な見積りの一意制約の下で新しい版の見積りを作れる() {
        Submitted submitted = approvedByShipper();

        assertThat(revise(submitted)).isInstanceOf(RevisionOutcome.Revised.class);

        assertThat(quotationOf(submitted, 1).status()).isEqualTo(QuotationStatus.VOIDED);
        assertThat(requestOf(submitted).status()).isEqualTo(TransportRequestStatus.UNDER_REVIEW);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM quotation.transport_request_version WHERE transport_request_id = ?",
                        Integer.class,
                        submitted.id().value()))
                .as("旧版を残す")
                .isEqualTo(2);
        reviews.approve(
                new ApproveTransportRequestCommand(submitted.number(), 2, SALES.userId(), "変更した条件を確認した"), SALES);
        assertThat(quotations.calculate(
                        new CalculateQuotationCommand(submitted.number(), QuotationFixture.completeInput()), SALES))
                .isInstanceOf(CalculationOutcome.Calculated.class);
    }

    @Test
    void 本予約を確定すると知らせを待たずに予約確定済みになり条件を変更できない() {
        Submitted submitted = approvedByShipper();

        assertThat(confirm(submitted)).isInstanceOf(BookingConfirmationOutcome.Confirmed.class);

        assertThat(requestOf(submitted).status()).isEqualTo(TransportRequestStatus.BOOKED);
        assertThat(revise(submitted)).isEqualTo(new RevisionOutcome.Rejected(RevisionRejection.NOT_REVISABLE));
        assertThat(quotationOf(submitted, 1).status()).isEqualTo(QuotationStatus.APPROVED);
    }

    @Test
    void 本予約の確定のコミットの前に始めた条件の変更は成立しない() throws Exception {
        Submitted submitted = approvedByShipper();
        CountDownLatch confirmedNotCommitted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<BookingConfirmationOutcome> booking = executor.submit(() -> transactionTemplate.execute(status -> {
                BookingConfirmationOutcome outcome = confirm(submitted);
                confirmedNotCommitted.countDown();
                awaitLatch(commit);
                return outcome;
            }));
            awaitLatch(confirmedNotCommitted);
            Future<RevisionOutcome> revision = executor.submit(() -> revise(submitted));
            // 変更は本予約の確定が押さえた行を待つ。待っていることを確かめてから、確定をコミットする
            assertThatThrownBy(() -> revision.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            commit.countDown();

            assertThat(booking.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(BookingConfirmationOutcome.Confirmed.class);
            // 変更は、競合の例外で戻るか、結果の値で断られる。成立はしない
            Object result;
            try {
                result = revision.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                result = e.getCause();
            }
            assertThat(result).isNotInstanceOf(RevisionOutcome.Revised.class);
        } finally {
            commit.countDown();
            executor.shutdownNow();
        }
        assertThat(requestOf(submitted).status()).isEqualTo(TransportRequestStatus.BOOKED);
        assertThat(requestOf(submitted).currentVersion().versionNo()).isEqualTo(1);
        assertThat(quotationOf(submitted, 1).status()).isEqualTo(QuotationStatus.APPROVED);
    }

    private BookingConfirmationOutcome confirm(Submitted submitted) {
        return bookings.confirm(
                new ConfirmBookingCommand(CommandId.random(), submitted.number().text(), 1, SALES, true));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("もう一方のトランザクションが進みません");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Submitted approvedByShipper() {
        SubmissionOutcome outcome = transportRequests.submit(
                new SubmitTransportRequestCommand(SHIPPER, SHIPPER_USER, ShipmentTermsFixture.completeInput()));
        assertThat(outcome).isInstanceOf(SubmissionOutcome.Submitted.class);
        SubmissionOutcome.Submitted accepted = (SubmissionOutcome.Submitted) outcome;
        Submitted submitted = new Submitted(accepted.transportRequestId(), accepted.number());
        reviews.approve(new ApproveTransportRequestCommand(submitted.number(), 1, SALES.userId(), "条件を確認した"), SALES);
        quotations.calculate(
                new CalculateQuotationCommand(submitted.number(), QuotationFixture.completeInput()), SALES);
        quotations.present(new PresentQuotationCommand(submitted.number(), 1, SALES.userId()), SALES);
        awaitStatus(submitted, TransportRequestStatus.QUOTED);
        responses.requestRouteDesign(new RequestRouteDesignCommand(submitted.number(), 1, SHIPPER, SHIPPER_USER));
        awaitStatus(submitted, TransportRequestStatus.ROUTING);
        routes.assign(quotationOf(submitted, 1).id().value(), QuotationFixture.assignedRoute());
        awaitStatus(submitted, TransportRequestStatus.AWAITING_APPROVAL);
        responses.approve(new ApproveQuotationCommand(submitted.number(), 1, SHIPPER, SHIPPER_USER));
        awaitStatus(submitted, TransportRequestStatus.READY_TO_BOOK);
        return submitted;
    }

    private RevisionOutcome revise(Submitted submitted) {
        return transportRequests.revise(new ReviseTransportRequestCommand(
                submitted.number(), SHIPPER, SHIPPER_USER, 1, ShipmentTermsFixture.completeInput(), List.of()));
    }

    private TransportRequest requestOf(Submitted submitted) {
        return transportRequestRepository.findById(submitted.id()).orElseThrow();
    }

    private Quotation quotationOf(Submitted submitted, int quotationNo) {
        return quotationRepository
                .findByTransportRequestIdAndNo(submitted.id(), quotationNo)
                .orElseThrow();
    }

    private void awaitStatus(Submitted submitted, TransportRequestStatus status) {
        await(() -> requestOf(submitted).status() == status, "輸送要求が " + status + " にならない");
    }

    private static void await(BooleanSupplier condition, String message) {
        Instant limit = Instant.now().plus(Duration.ofSeconds(30));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(limit)) {
                throw new AssertionError(message);
            }
            Thread.onSpinWait();
        }
    }

    private record Submitted(TransportRequestId id, TransportRequestNumber number) {}
}
