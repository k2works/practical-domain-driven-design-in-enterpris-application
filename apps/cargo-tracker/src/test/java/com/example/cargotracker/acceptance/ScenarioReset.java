package com.example.cargotracker.acceptance;

import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.MutableClock;
import io.cucumber.java.Before;
import java.time.Instant;

/**
 * シナリオごとに時刻・メモリ上のリポジトリ・ためたイベントを初期化し、シナリオの順序に依存しないようにする。
 */
public class ScenarioReset {

    private final MutableClock clock;
    private final InMemoryTransportRequestRepository transportRequestRepository;
    private final InMemoryKpiObservationRepository kpiObservationRepository;
    private final DeferredEventDelivery delivery;

    public ScenarioReset(
            MutableClock clock,
            InMemoryTransportRequestRepository transportRequestRepository,
            InMemoryKpiObservationRepository kpiObservationRepository,
            DeferredEventDelivery delivery) {
        this.clock = clock;
        this.transportRequestRepository = transportRequestRepository;
        this.kpiObservationRepository = kpiObservationRepository;
        this.delivery = delivery;
    }

    @Before
    public void reset() {
        clock.setInstant(Instant.EPOCH);
        transportRequestRepository.clear();
        kpiObservationRepository.clear();
        delivery.clear();
    }
}
