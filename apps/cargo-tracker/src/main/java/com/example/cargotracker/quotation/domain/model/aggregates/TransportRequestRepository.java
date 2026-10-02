package com.example.cargotracker.quotation.domain.model.aggregates;

import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestNumber;
import java.util.Optional;

/**
 * 輸送要求のリポジトリ（送信ポート）。実装は infrastructure に置く。
 */
public interface TransportRequestRepository {

    void save(TransportRequest transportRequest);

    Optional<TransportRequest> findById(TransportRequestId id);

    /** 業務番号で輸送要求を探す。業務番号は一意なので、見つかるのは高々 1 件。 */
    Optional<TransportRequest> findByNumber(TransportRequestNumber number);
}
