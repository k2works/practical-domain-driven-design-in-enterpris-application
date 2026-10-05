package com.example.cargotracker.acceptance;

import com.example.cargotracker.identity.acceptance.InMemoryKpiObservationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryQuotationRepository;
import com.example.cargotracker.quotation.acceptance.InMemoryRequiredDocumentStorage;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestNumberIssuer;
import com.example.cargotracker.quotation.acceptance.InMemoryTransportRequestRepository;
import com.example.cargotracker.shared.acceptance.DeferredEventDelivery;
import com.example.cargotracker.shared.acceptance.MutableClock;
import io.cucumber.java.Before;
import java.time.Instant;

/**
 * シナリオごとに時刻・メモリ上のリポジトリ・採番・書類の保存・ためたイベントを初期化し、シナリオの順序に依存しないようにする。
 */
public class ScenarioReset {

    private final MutableClock clock;
    private final InMemoryTransportRequestRepository transportRequestRepository;
    private final InMemoryTransportRequestNumberIssuer transportRequestNumberIssuer;
    private final InMemoryKpiObservationRepository kpiObservationRepository;
    private final DeferredEventDelivery delivery;
    private final InMemoryRequiredDocumentStorage documentStorage;
    private final InMemoryQuotationRepository quotationRepository;

    public ScenarioReset(
            MutableClock clock,
            InMemoryTransportRequestRepository transportRequestRepository,
            InMemoryTransportRequestNumberIssuer transportRequestNumberIssuer,
            InMemoryKpiObservationRepository kpiObservationRepository,
            DeferredEventDelivery delivery,
            InMemoryRequiredDocumentStorage documentStorage,
            InMemoryQuotationRepository quotationRepository) {
        this.clock = clock;
        this.transportRequestRepository = transportRequestRepository;
        this.transportRequestNumberIssuer = transportRequestNumberIssuer;
        this.kpiObservationRepository = kpiObservationRepository;
        this.delivery = delivery;
        this.documentStorage = documentStorage;
        this.quotationRepository = quotationRepository;
    }

    @Before
    public void reset() {
        clock.setInstant(Instant.EPOCH);
        transportRequestRepository.clear();
        transportRequestNumberIssuer.clear();
        kpiObservationRepository.clear();
        delivery.clear();
        documentStorage.clear();
        quotationRepository.clear();
    }
}
