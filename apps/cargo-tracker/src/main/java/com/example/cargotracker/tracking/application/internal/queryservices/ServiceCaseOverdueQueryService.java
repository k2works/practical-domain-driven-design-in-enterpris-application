package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受領期限を過ぎた有人案件の照会（US-20 AC3、SVC-04、OBS-05。Bolt 40-9）。期限の超過は読むときに判定する（Bolt 39g4 の鮮度超過と同じ方針）。
 * 状態への書き込み（上位対応中への遷移）と上位の運用責任者への通知は、定期処理と運用チャンネルの設定とあわせて W10 で作る。社内の運用役割だけが読める
 * （認可の定めは台帳 D-159）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ServiceCaseOverdueQueryService {

    /** 返す件数の上限（S-14 の一覧は Bolt 40-11 でページを分ける）。 */
    static final int LIMIT = 200;

    private final ServiceCaseRepository repository;
    private final Clock clock;
    private final java.util.function.Predicate<AuthenticatedActor> permitted;

    public ServiceCaseOverdueQueryService(
            ServiceCaseRepository repository, Clock clock, java.util.function.Predicate<AuthenticatedActor> permitted) {
        this.repository = repository;
        this.clock = clock;
        this.permitted = permitted;
    }

    /**
     * 受領期限を過ぎた案件を、期限の古い順に返す。社内の運用役割でなければ空の結果（見つからないのではなく、権限がない）を返す。
     *
     * @return 権限があれば期限超過の一覧（空もあり得る）、権限がなければ空の {@link Optional}
     */
    @Transactional(readOnly = true)
    public Optional<List<OverdueServiceCase>> overdue(AuthenticatedActor actor) {
        if (!permitted.test(actor)) {
            return Optional.empty();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        return Optional.of(repository.findReceiptOverdue(now, LIMIT).stream()
                .map(serviceCase -> new OverdueServiceCase(
                        serviceCase.number(),
                        serviceCase.kind(),
                        serviceCase.priority(),
                        serviceCase.department(),
                        serviceCase.status(),
                        serviceCase.receiveDueAt(),
                        Duration.between(serviceCase.receiveDueAt().instant(), now.instant()),
                        serviceCase.requesterNotice()))
                .toList());
    }
}
