package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路設計案件の入力ポート（経路設計者が使う。US-06）。トランザクションの境界になる。骨組み（ステップ 3 の Red）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseCommandService {

    private final RoutingCaseRepository repository;
    private final VoyageRepository voyageRepository;
    private final ConnectionRuleRepository connectionRuleRepository;
    private final Clock clock;

    public RoutingCaseCommandService(
            RoutingCaseRepository repository,
            VoyageRepository voyageRepository,
            ConnectionRuleRepository connectionRuleRepository,
            Clock clock) {
        this.repository = repository;
        this.voyageRepository = voyageRepository;
        this.connectionRuleRepository = connectionRuleRepository;
        this.clock = clock;
    }

    /** 経路候補を算出・再算出する。骨組み。 */
    @Transactional
    public CandidateCalculationOutcome calculateCandidates(CalculateCandidatesCommand command) {
        return new CandidateCalculationOutcome.NotFound();
    }
}
