package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 経路設計案件の照会（S-05・S-06。経路設計者が使う。Bolt 17）。社内の照会で、荷主企業で絞らない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseQueryService {

    private final RoutingCaseRepository repository;

    public RoutingCaseQueryService(RoutingCaseRepository repository) {
        this.repository = repository;
    }

    /** 案件一覧（S-05）。依頼時刻の新しい順。 */
    public List<RoutingCaseSummary> listCases() {
        return repository.findSummaries();
    }

    /** 案件と、いまの経路版の候補（S-06）。 */
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
        return repository.findByNumber(number);
    }
}
