package com.example.cargotracker.quotation.application.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.aggregates.ConcurrentTransportRequestUpdateException;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTermsFixture;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestStatus;
import com.example.cargotracker.shared.domain.CompanyId;
import com.example.cargotracker.shared.domain.UserId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** 輸送要求を進める部品の判定の口と「届いていても書く」（ADR-018。Bolt 35b2）。 */
class TransportRequestProgressionTest {

    private static final UtcInstant NOW = new UtcInstant(Instant.parse("2026-10-07T05:00:00Z"));

    private final ConflictingOnce repository = new ConflictingOnce();
    private final AtomicInteger gateCalls = new AtomicInteger();
    private final QuotationGate countingGate = _ -> {
        gateCalls.incrementAndGet();
        return QuotationGate.Verdict.PROCEED;
    };

    @Test
    void 楽観ロックの競合でやり直すたびに判定の口を呼び直す() {
        TransportRequest request = stored();
        repository.conflictNext = true;

        TransportRequestProgression.Result result =
                progression().checking(countingGate).advance(request.id().value(), UUID.randomUUID(), 1);

        assertThat(result).isEqualTo(TransportRequestProgression.Result.ADVANCED);
        assertThat(gateCalls).as("最初の試みとやり直しの 2 回").hasValue(2);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.QUOTED);
    }

    @Test
    void 届いていても書く指定なら版を上げ指定がなければ書かない() {
        TransportRequest request = stored();
        progression().advance(request.id().value(), UUID.randomUUID(), 1);
        long version = repository.findById(request.id()).orElseThrow().aggregateVersion();

        assertThat(progression().advance(request.id().value(), UUID.randomUUID(), 1))
                .isEqualTo(TransportRequestProgression.Result.ALREADY_REACHED);
        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(version);
        assertThat(progression().writingWhenReached().advance(request.id().value(), UUID.randomUUID(), 1))
                .isEqualTo(TransportRequestProgression.Result.ALREADY_REACHED);
        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(version + 1);
    }

    @Test
    void 判定の口が決着済みとしたら進めず書かない() {
        TransportRequest request = stored();
        long version = repository.findById(request.id()).orElseThrow().aggregateVersion();

        TransportRequestProgression.Result result = progression()
                .checking(_ -> QuotationGate.Verdict.SETTLED)
                .writingWhenReached()
                .advance(request.id().value(), UUID.randomUUID(), 1);

        assertThat(result).isEqualTo(TransportRequestProgression.Result.ALREADY_REACHED);
        assertThat(repository.findById(request.id()).orElseThrow().status()).isEqualTo(TransportRequestStatus.QUOTING);
        assertThat(repository.findById(request.id()).orElseThrow().aggregateVersion())
                .isEqualTo(version);
    }

    private TransportRequestProgression progression() {
        return new TransportRequestProgression(
                repository,
                LoggerFactory.getLogger(TransportRequestProgressionTest.class),
                "DE-03",
                TransportRequestStatus.QUOTED,
                "見積提示済み",
                TransportRequest::markQuotationPresented);
    }

    private TransportRequest stored() {
        UserId staff = new UserId(UUID.randomUUID());
        TransportRequest request = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                new TransportRequestNumber(2026, 1),
                new CompanyId(UUID.randomUUID()),
                ShipmentTermsFixture.generalCargo(),
                staff,
                NOW);
        repository.save(request);
        TransportRequest found = repository.findById(request.id()).orElseThrow();
        found.approve(1, staff, "根拠", NOW);
        repository.update(found);
        return found;
    }

    /** 次の 1 回の更新だけ、ほかの更新が先に保存されていたことにする。 */
    private static final class ConflictingOnce extends InMemoryTransportRequestRepository {

        private boolean conflictNext;

        @Override
        public synchronized void update(TransportRequest transportRequest) {
            if (conflictNext) {
                conflictNext = false;
                throw new ConcurrentTransportRequestUpdateException(
                        transportRequest.id(), transportRequest.aggregateVersion());
            }
            super.update(transportRequest);
        }
    }
}
