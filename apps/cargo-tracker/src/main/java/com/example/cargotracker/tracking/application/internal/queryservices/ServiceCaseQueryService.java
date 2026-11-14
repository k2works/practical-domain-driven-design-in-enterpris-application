package com.example.cargotracker.tracking.application.internal.queryservices;

import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.Role;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.application.internal.commandservices.ServiceCaseAuthorization;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCase;
import com.example.cargotracker.tracking.domain.model.aggregates.ServiceCaseRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.CaseNumber;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 有人案件の一覧（S-14）と詳細（S-15）の照会（US-20。Bolt 40-11）。期限超過は読むときに判定する（Bolt 40-9 と同じ方針）。社内の運用役割だけが読める
 * （認可の定めは台帳 D-159。権限がなければ空の結果で、見つからないのと区別しない）。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code TrackingConfiguration} が担う。
 */
@Service
public class ServiceCaseQueryService {

    /** 一覧の件数の上限。 */
    static final int LIMIT = 200;

    private final ServiceCaseRepository repository;
    private final Clock clock;
    private final Predicate<AuthenticatedActor> permitted;

    public ServiceCaseQueryService(
            ServiceCaseRepository repository, Clock clock, Predicate<AuthenticatedActor> permitted) {
        this.repository = repository;
        this.clock = clock;
        this.permitted = permitted;
    }

    /**
     * 絞り込みに合う案件を、受領期限の古い順に上限まで返す。
     *
     * @return 権限があれば一覧（空もあり得る）、権限がなければ空の {@link Optional}
     */
    @Transactional(readOnly = true)
    public Optional<ServiceCaseList> list(AuthenticatedActor actor, ServiceCaseListFilter filter) {
        if (!permitted.test(actor)) {
            return Optional.empty();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        // 打ち切りかどうかを知るため、上限より 1 件多く読む
        List<ServiceCase> found = filter == ServiceCaseListFilter.OVERDUE
                ? repository.findReceiptOverdue(now, LIMIT + 1)
                : repository.findByStatuses(filter.statuses(), LIMIT + 1);
        boolean truncated = found.size() > LIMIT;
        List<ServiceCaseListItem> items = found.stream()
                .limit(LIMIT)
                .map(serviceCase -> new ServiceCaseListItem(
                        serviceCase.number(),
                        serviceCase.kind(),
                        serviceCase.priority(),
                        serviceCase.department(),
                        serviceCase.status(),
                        serviceCase.receiveDueAt(),
                        serviceCase.isReceiptOverdue(now)))
                .toList();
        return Optional.of(new ServiceCaseList(items, truncated, LIMIT));
    }

    /**
     * 受付番号の案件の詳細。ない案件と、その案件を扱えない役割は、ともに空（存在を明かさない）。案件ごとの認可は操作のサービスと同じ
     * {@link ServiceCaseAuthorization#canAccess}（社内の運用役割はどの案件も、荷主担当者は自社の案件だけ。W8 の週全体レビュー D。荷主に詳細を開く
     * W9 で、役割の述語だけを広げて他社の案件が見えることを防ぐ）。
     */
    @Transactional(readOnly = true)
    public Optional<ServiceCaseDetail> detail(AuthenticatedActor actor, CaseNumber number) {
        UtcInstant now = new UtcInstant(clock.instant());
        return repository
                .findByNumber(number)
                .filter(c -> ServiceCaseAuthorization.canAccess(actor, c))
                .map(c -> new ServiceCaseDetail(c, c.isReceiptOverdue(now)));
    }

    /**
     * 荷主担当者が問い合わせの詳細を開く（C-15。US-23。Bolt 42）。自社の案件なら詳細、他社の案件なら範囲外（呼び手が試行を記録して 404）、
     * ない案件・荷主担当者でない利用者は見つからない。
     */
    @Transactional(readOnly = true)
    public CustomerServiceCaseLookup customerDetail(AuthenticatedActor actor, CaseNumber number) {
        if (!actor.hasRole(Role.SHIPPER)) {
            return new CustomerServiceCaseLookup.NotFound();
        }
        UtcInstant now = new UtcInstant(clock.instant());
        return repository
                .findByNumber(number)
                .<CustomerServiceCaseLookup>map(c -> ServiceCaseAuthorization.canAccess(actor, c)
                        ? new CustomerServiceCaseLookup.Found(new ServiceCaseDetail(c, c.isReceiptOverdue(now)))
                        : new CustomerServiceCaseLookup.OutOfScope())
                .orElseGet(CustomerServiceCaseLookup.NotFound::new);
    }
}
