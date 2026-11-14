package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commands.OpenServiceCaseCommand;
import com.example.cargotracker.tracking.application.internal.commands.ReceiveServiceCaseCommand;
import com.example.cargotracker.tracking.domain.model.aggregates.BusinessCalendarRepository;
import com.example.cargotracker.tracking.domain.model.aggregates.ConcurrentServiceCaseUpdateException;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseNumberIssuer;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.ServiceCaseStatus;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 有人案件の入力ポート（US-20 AC1・AC2・AC8。Bolt 40-7）。トランザクションの境界になる。起票は認可 → 必須項目 → 重複 → 期限の計算 → 採番 → 保存の順で、
 * 先の段階で止まれば受付番号を使わない（採番は呼び元のトランザクションの中なので、保存が失敗しても戻る）。受領は認可 → 読み出し → 状態 → 更新。
 * 認可の定めは {@link ServiceCaseAuthorization}（台帳 D-159。仮）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ServiceCaseCommandService {

    private final ServiceCaseRepository repository;
    private final Clock clock;
    private final ServiceCaseIntake intake;

    public ServiceCaseCommandService(
            ServiceCaseRepository repository,
            ServiceCaseNumberIssuer numberIssuer,
            BusinessCalendarRepository calendars,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.intake = new ServiceCaseIntake(repository, numberIssuer, calendars, clock);
    }

    /** 有人案件を起票する（US-20 AC1・AC8）。 */
    @Transactional
    public OpenServiceCaseOutcome open(AuthenticatedActor actor, OpenServiceCaseCommand command) {
        if (!ServiceCaseAuthorization.canOpen(actor, command.draft())) {
            return new OpenServiceCaseOutcome.NotPermitted();
        }
        return intake.open(command.draft(), actor.userId(), Optional.empty());
    }

    /** 有人案件を受領する（US-20 AC2）。担当者は操作者、受領時刻は時計から得る。 */
    @Transactional
    public ReceiveServiceCaseOutcome receive(AuthenticatedActor actor, ReceiveServiceCaseCommand command) {
        if (!ServiceCaseAuthorization.canReceive(actor)) {
            return new ReceiveServiceCaseOutcome.NotPermitted();
        }
        Optional<ServiceCase> found = repository.findByNumber(command.number());
        if (found.isEmpty()) {
            return new ReceiveServiceCaseOutcome.NotFound();
        }
        ServiceCase serviceCase = found.get();
        ServiceCaseStatus status = serviceCase.status();
        if (status != ServiceCaseStatus.RECEIVED && status != ServiceCaseStatus.ESCALATED) {
            return new ReceiveServiceCaseOutcome.NotReceivable(status);
        }
        ServiceCase received = serviceCase.receive(actor.userId(), new UtcInstant(clock.instant()));
        try {
            repository.update(received, actor.userId());
        } catch (ConcurrentServiceCaseUpdateException _) {
            return new ReceiveServiceCaseOutcome.Conflict();
        }
        return new ReceiveServiceCaseOutcome.Received(command.number());
    }
}
