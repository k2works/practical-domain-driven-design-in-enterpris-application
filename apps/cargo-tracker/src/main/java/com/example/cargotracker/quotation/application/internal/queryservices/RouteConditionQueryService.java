package com.example.cargotracker.quotation.application.internal.queryservices;

import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequest;
import com.example.cargotracker.quotation.domain.model.aggregates.TransportRequestRepository;
import com.example.cargotracker.quotation.domain.model.valueobjects.TransportRequestId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 経路条件の照会の入力ポート。見積りの公開 API のアダプター（{@code interfaces.api.internal}）が委ね、経路条件の型に変える
 * （2026-10-09 に公開 API を interfaces.api へ移した）。
 *
 * <p> * 経路条件の照会の実装（見積りの公開 API。Bolt 17）。輸送要求の現在の版の輸送条件から、経路設計が使う項目を写す。
 * 輸送要求は現在の版だけを読み出すため、依頼の後に再提出された（現在の版が違う）ときは空を返す。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code QuotationConfiguration} が担う。
 */
@Service
public class RouteConditionQueryService {

    private final TransportRequestRepository repository;

    public RouteConditionQueryService(TransportRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * 輸送要求の、指定した版が現在の版なら返す。
     *
     * @param transportRequestId 輸送要求 ID
     * @param transportRequestVersionNo 版番号
     * @return 輸送要求。ない、または現在の版でなければ空
     */
    public Optional<TransportRequest> find(UUID transportRequestId, int transportRequestVersionNo) {
        return repository
                .findById(new TransportRequestId(transportRequestId))
                .filter(request -> request.currentVersion().versionNo() == transportRequestVersionNo);
    }
}
