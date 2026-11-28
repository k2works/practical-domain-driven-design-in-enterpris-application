package com.example.cargotracker.identity.application.internal.commandservices;

import com.example.cargotracker.identity.application.internal.commands.RegisterKpiBaselineCommand;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KPI の基準値を登録する入力ポート（US-21 AC5、KPI-INV-02。W9 の Bolt 44-2。台帳 D-172。仮）。登録できるのは監査担当者だけ（人が選んだ。URL の規則も同じ）。
 * 同じ KPI の最新の基準値があれば、それを指す新しい記録にする（登録後は変えず、訂正は新しい記録で行う）。登録者と登録時刻は呼び手から受けず、認証の主体と
 * 時計から決める。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class KpiBaselineCommandService {

    private final KpiBaselineRepository repository;
    private final Clock clock;

    public KpiBaselineCommandService(KpiBaselineRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public KpiBaselineOutcome register(AuthenticatedActor actor, RegisterKpiBaselineCommand command) {
        if (!actor.hasRole(Role.AUDITOR)) {
            return new KpiBaselineOutcome.NotPermitted();
        }
        Optional<UUID> supersedes = repository.findLatest(command.kpi()).map(KpiBaseline::id);
        KpiBaseline baseline;
        try {
            baseline = KpiBaseline.register(
                    command.kpi(),
                    command.periodFrom(),
                    command.periodTo(),
                    command.valueP50(),
                    command.valueP90(),
                    command.method(),
                    actor.userId(),
                    new UtcInstant(clock.instant()),
                    supersedes);
        } catch (IllegalArgumentException e) {
            return new KpiBaselineOutcome.InvalidInput(e.getMessage());
        }
        repository.save(baseline);
        return new KpiBaselineOutcome.Registered(baseline);
    }
}
