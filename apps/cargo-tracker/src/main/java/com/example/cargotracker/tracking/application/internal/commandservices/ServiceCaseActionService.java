package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.CloseServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.RecordServiceCaseAnswerCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReopenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReviseServiceCaseAnswerDueCommand;
import com.example.cargotracker.tracking.application.internal.commands.UpdateServiceCaseProgressCommand;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentServiceCaseUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseClosing;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.time.Clock;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 有人案件の進捗・回答・終結・再開・回答期限の変更の入力ポート（US-20 AC4〜AC7、UC-20。Bolt 40-8）。トランザクションの境界になる。認可は認可の定め
 * （{@link ServiceCaseAuthorization}。台帳 D-159）→ 読み出し → 荷主担当者は自社の案件だけ（他社・ない受付番号は「見つからない」）→ 状態 → 更新の順。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ServiceCaseActionService {

    private final ServiceCaseRepository repository;
    private final Clock clock;

    public ServiceCaseActionService(ServiceCaseRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** 進捗を更新する（AC4）。 */
    @Transactional
    public ServiceCaseActionOutcome updateProgress(AuthenticatedActor actor, UpdateServiceCaseProgressCommand command) {
        return run(
                actor,
                command.number(),
                () -> ServiceCaseAuthorization.canOperate(actor),
                serviceCase -> new Step.Next(
                        serviceCase.updateProgress(command.content(), command.nextUpdateAt(), actor.userId(), now())));
    }

    /** 回答を記録する（AC5）。 */
    @Transactional
    public ServiceCaseActionOutcome recordAnswer(AuthenticatedActor actor, RecordServiceCaseAnswerCommand command) {
        return run(
                actor,
                command.number(),
                () -> ServiceCaseAuthorization.canOperate(actor),
                serviceCase -> new Step.Next(serviceCase.recordAnswer(command.content(), actor.userId(), now())));
    }

    /** 終結する（AC5・AC7）。復旧照合が必要で未完了なら終結せず、不足する照合結果を返す。 */
    @Transactional
    public ServiceCaseActionOutcome close(AuthenticatedActor actor, CloseServiceCaseCommand command) {
        return run(
                actor,
                command.number(),
                () -> ServiceCaseAuthorization.canOperate(actor),
                serviceCase -> switch (serviceCase.close()) {
                    case ServiceCaseClosing.Closed(ServiceCase closed) -> new Step.Next(closed);
                    case ServiceCaseClosing.ReconciliationIncomplete(var missing) ->
                        new Step.Stop(new ServiceCaseActionOutcome.ReconciliationIncomplete(missing));
                });
    }

    /** 再開する（AC6。US-23 AC2）。荷主担当者は自社の案件だけ。 */
    @Transactional
    public ServiceCaseActionOutcome reopen(AuthenticatedActor actor, ReopenServiceCaseCommand command) {
        return run(
                actor,
                command.number(),
                () -> ServiceCaseAuthorization.canReopen(actor),
                serviceCase -> new Step.Next(serviceCase.reopen(command.customerResponse(), now())));
    }

    /** 回答期限を後ろへ変更する（UC-20。期限を超える場合）。理由は必須。 */
    @Transactional
    public ServiceCaseActionOutcome reviseAnswerDue(
            AuthenticatedActor actor, ReviseServiceCaseAnswerDueCommand command) {
        return run(
                actor,
                command.number(),
                () -> ServiceCaseAuthorization.canOperate(actor),
                serviceCase -> new Step.Next(
                        serviceCase.reviseAnswerDue(command.reason(), command.newDue(), actor.userId(), now())));
    }

    private ServiceCaseActionOutcome run(
            AuthenticatedActor actor, CaseNumber number, BooleanSupplier permitted, Function<ServiceCase, Step> step) {
        if (!permitted.getAsBoolean()) {
            return new ServiceCaseActionOutcome.NotPermitted();
        }
        Optional<ServiceCase> found = repository.findByNumber(number);
        if (found.isEmpty() || !ServiceCaseAuthorization.canAccess(actor, found.get())) {
            return new ServiceCaseActionOutcome.NotFound();
        }
        ServiceCase current = found.get();
        Step result;
        try {
            result = step.apply(current);
        } catch (IllegalStateException _) {
            return new ServiceCaseActionOutcome.InvalidState(current.status());
        } catch (IllegalArgumentException e) {
            return new ServiceCaseActionOutcome.InvalidInput(e.getMessage());
        }
        if (result instanceof Step.Stop(ServiceCaseActionOutcome stopped)) {
            return stopped;
        }
        ServiceCase next = ((Step.Next) result).serviceCase();
        try {
            repository.update(next, actor.userId());
        } catch (ConcurrentServiceCaseUpdateException _) {
            return new ServiceCaseActionOutcome.Conflict();
        }
        return new ServiceCaseActionOutcome.Done(number, next.status());
    }

    private UtcInstant now() {
        return new UtcInstant(clock.instant());
    }

    /** 状態の遷移の結果。次の状態の案件か、そこで止める結果。 */
    private sealed interface Step {
        record Next(ServiceCase serviceCase) implements Step {}

        record Stop(ServiceCaseActionOutcome outcome) implements Step {}
    }
}
