package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Optional;

/**
 * 輸送要求のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface TransportRequestRepository {

    void save(TransportRequest transportRequest);

    Optional<TransportRequest> findById(TransportRequestId id);
}
