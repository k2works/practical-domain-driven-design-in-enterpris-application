package com.example.cargotracker.routing.application.internal.commandservices;

import com.example.cargotracker.routing.application.internal.commands.CalculateCandidatesCommand;
import com.example.cargotracker.routing.application.internal.commands.ConfirmRouteCommand;
import com.example.cargotracker.routing.domain.events.RouteConfirmed;
import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.ConnectionRuleRepository;
import com.example.cargotracker.routing.domain.model.aggregates.RouteConfirmationRejected;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteApprover;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路設計案件の入力ポート（経路設計者が使う。US-06・US-07）。トランザクションの境界になる。
 * 判定時刻は Clock から得る。航海と接続時間規則を読み込み、候補探索と制約適合判定に渡す（どちらもリポジトリを呼ばない）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseCommandService {

    private final RoutingCaseRepository repository;
    private final VoyageRepository voyageRepository;
    private final ConnectionRuleRepository connectionRuleRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final RouteCandidateFinder finder = new RouteCandidateFinder();
    private final ConstraintEvaluator evaluator = new ConstraintEvaluator();

    public RoutingCaseCommandService(
            RoutingCaseRepository repository,
            VoyageRepository voyageRepository,
            ConnectionRuleRepository connectionRuleRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.voyageRepository = voyageRepository;
        this.connectionRuleRepository = connectionRuleRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 経路候補を算出・再算出する（US-06 AC1〜AC3）。 */
    @Transactional
    public CandidateCalculationOutcome calculateCandidates(CalculateCandidatesCommand command) {
        Optional<RoutingCase> found = repository.findByNumber(command.number());
        if (found.isEmpty()) {
            return new CandidateCalculationOutcome.NotFound();
        }
        RoutingCase routingCase = found.get();
        if (routingCase.aggregateVersion() != command.expectedVersion()) {
            return new CandidateCalculationOutcome.Conflict();
        }
        CandidateCalculation calculation = routingCase.calculateCandidates(
                voyageRepository.findAll(),
                connectionRuleRepository.findAll(),
                new UtcInstant(clock.instant()),
                finder,
                evaluator);
        try {
            repository.update(routingCase, command.operator().value());
        } catch (ConcurrentRoutingCaseUpdateException _) {
            return new CandidateCalculationOutcome.Conflict();
        }
        return new CandidateCalculationOutcome.Calculated(routingCase.number(), calculation);
    }

    /**
     * 判断根拠を記録して経路を確定する（US-07 AC1・AC2）。確定の時刻（承認 commit 時刻）は Clock から得て、その時刻で有効な
     * 接続時間規則で判定し直す。確定したら DE-05 を発行する（購読は US-24 AC4）。
     */
    @Transactional
    public RouteConfirmationOutcome confirm(ConfirmRouteCommand command) {
        Optional<RoutingCase> found = repository.findByNumber(command.number());
        if (found.isEmpty()) {
            return new RouteConfirmationOutcome.NotFound();
        }
        RoutingCase routingCase = found.get();
        if (routingCase.aggregateVersion() != command.expectedVersion()) {
            return new RouteConfirmationOutcome.Conflict();
        }
        RouteConfirmed confirmed;
        try {
            confirmed = routingCase.confirm(
                    command.candidateNo(),
                    command.rationale(),
                    new RouteApprover(command.approver().value(), command.routeDesigner()),
                    connectionRuleRepository.findAll(),
                    new UtcInstant(clock.instant()),
                    evaluator);
        } catch (RouteConfirmationRejected rejected) {
            return new RouteConfirmationOutcome.Rejected(rejected.reason());
        }
        try {
            repository.update(routingCase, command.approver().value());
        } catch (ConcurrentRoutingCaseUpdateException _) {
            return new RouteConfirmationOutcome.Conflict();
        }
        eventPublisher.publishEvent(confirmed);
        return new RouteConfirmationOutcome.Confirmed(
                routingCase.number(), confirmed.routeVersionNo(), command.candidateNo());
    }
}
