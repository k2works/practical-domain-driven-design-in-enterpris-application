package com.example.cargotracker.quotation.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.application.internal.commands.ApproveTransportRequestCommand;
import com.example.cargotracker.quotation.application.internal.commands.SendBackTransportRequestCommand;
import com.example.cargotracker.quotation.domain.events.TransportRequestReviewed;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ReviewDecision;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestRejection;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransportRequestReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T03:00:00Z");
    private static final TransportRequestNumber NUMBER = new TransportRequestNumber(2026, 1);
    private static final UserId REVIEWER = new UserId(UUID.randomUUID());

    private final List<Object> published = new ArrayList<>();
    private InMemoryTransportRequestRepository repository;
    private TransportRequestReviewService service;
    private TransportRequestId id;

    @BeforeEach
    void 審査中の輸送要求を保存しておく() {
        repository = new InMemoryTransportRequestRepository();
        service = new TransportRequestReviewService(repository, published::add, Clock.fixed(NOW, ZoneOffset.UTC));
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                NUMBER,
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                new UserId(UUID.randomUUID()),
                new UtcInstant(Instant.parse("2026-10-05T01:00:00Z")));
        repository.save(request);
        id = request.id();
    }

    @Test
    void 確定するとClockの時刻で記録して保存しDE02を1回だけ発行する() {
        ReviewOutcome outcome = service.approve(new ApproveTransportRequestCommand(NUMBER, 1, REVIEWER, "確認した"));

        assertThat(outcome).isEqualTo(new ReviewOutcome.Reviewed(NUMBER, 1, ReviewDecision.APPROVED));
        assertThat(repository.findById(id).orElseThrow().reviewRecords())
                .singleElement()
                .satisfies(reviewRecord -> assertThat(reviewRecord.decidedAt()).isEqualTo(new UtcInstant(NOW)));
        assertThat(published)
                .containsExactly(
                        new TransportRequestReviewed(id.value(), 1, "APPROVED", REVIEWER, new UtcInstant(NOW)));
    }

    @Test
    void 受け付けなければ保存もイベントの発行もせず理由と現在の版を返す() {
        ReviewOutcome outcome = service.sendBack(new SendBackTransportRequestCommand(NUMBER, 2, REVIEWER, "理由", null));

        assertThat(outcome).isEqualTo(new ReviewOutcome.Rejected(TransportRequestRejection.STALE_VERSION, 1));
        assertThat(repository.findById(id).orElseThrow().reviewRecords()).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void 業務番号の輸送要求がなければ見つからないを返す() {
        assertThat(service.approve(
                        new ApproveTransportRequestCommand(new TransportRequestNumber(2026, 99), 1, REVIEWER, "確認")))
                .isInstanceOf(ReviewOutcome.NotFound.class);
    }

    @Test
    void ほかの更新が先に保存されていたら競合を返しイベントを発行しない() {
        InMemoryTransportRequestRepository conflicting = new InMemoryTransportRequestRepository() {
            @Override
            public void update(TransportRequest transportRequest) {
                throw new ConcurrentTransportRequestUpdateException(
                        transportRequest.id(), transportRequest.aggregateVersion());
            }
        };
        conflicting.save(repository.findById(id).orElseThrow());
        TransportRequestReviewService conflictingService =
                new TransportRequestReviewService(conflicting, published::add, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(conflictingService.approve(new ApproveTransportRequestCommand(NUMBER, 1, REVIEWER, "確認した")))
                .isInstanceOf(ReviewOutcome.Conflict.class);
        assertThat(published).isEmpty();
    }
}
