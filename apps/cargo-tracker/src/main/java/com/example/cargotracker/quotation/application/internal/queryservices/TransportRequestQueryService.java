package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * 輸送要求を照会する入力ポート。
 */
public class TransportRequestQueryService {

    private final TransportRequestRepository repository;

    public TransportRequestQueryService(TransportRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<TransportRequest> findById(TransportRequestId id) {
        return repository.findById(id);
    }
}
