package com.example.cargotracker.quotation.application.internal.commandservices;

import com.example.cargotracker.quotation.application.internal.commands.SubmitTransportRequestCommand;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.ShipmentTerms;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求のコマンドを受け付ける入力ポート。トランザクションの境界になる。
 */
public class TransportRequestCommandService {

    private final TransportRequestRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TransportRequestCommandService(
            TransportRequestRepository repository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 輸送要求を提出する。保存と同じトランザクションで DE-01 を発行する。
     */
    @Transactional
    public TransportRequestId submit(SubmitTransportRequestCommand command) {
        TransportRequest transportRequest = TransportRequest.submit(
                new TransportRequestId(UUID.randomUUID()),
                command.shipperCompanyId(),
                new ShipmentTerms(command.origin(), command.destination()),
                command.submittedBy(),
                new UtcInstant(clock.instant()));
        repository.save(transportRequest);
        transportRequest.domainEvents().forEach(eventPublisher::publishEvent);
        transportRequest.clearDomainEvents();
        return transportRequest.id();
    }
}
