package com.example.cargotracker.identity.application.internal.queryservices;

import com.example.cargotracker.identity.domain.model.aggregates.IncompleteEventPublicationCounter;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaseline;
import com.example.cargotracker.identity.domain.model.aggregates.KpiBaselineRepository;
import com.example.cargotracker.identity.domain.model.valueobjects.KpiKind;
import com.example.cargotracker.shared.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基準値とパイロット中の値を並べる照会と、未完了の発行の件数（US-21 AC5。W9 の Bolt 44-2。台帳 D-172。仮）。パイロット中の値は直近の週数ぶん
 * （今週を含む）をまとめた値で、PV-01 の合否の判定そのものではなく、判断の材料として並べる。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない。組み立ては {@code IdentityConfiguration} が担う。
 */
@Service
public class KpiComparisonQueryService {

    private final KpiWeeklyQueryService weeklyQueryService;
    private final KpiBaselineRepository baselines;
    private final IncompleteEventPublicationCounter publicationCounter;
    private final Function<UserId, String> registrantNames;

    /**
     * @param registrantNames 利用者の ID から表示名を引く（基準値の登録者を画面に示す）
     */
    public KpiComparisonQueryService(
            KpiWeeklyQueryService weeklyQueryService,
            KpiBaselineRepository baselines,
            IncompleteEventPublicationCounter publicationCounter,
            Function<UserId, String> registrantNames) {
        this.weeklyQueryService = weeklyQueryService;
        this.baselines = baselines;
        this.publicationCounter = publicationCounter;
        this.registrantNames = registrantNames;
    }

    @Transactional(readOnly = true)
    public KpiComparison compare(Instant now, int weeks) {
        Optional<KpiBaseline> kpi01 = baselines.findLatest(KpiKind.KPI_01);
        Optional<KpiBaseline> kpi02 = baselines.findLatest(KpiKind.KPI_02);
        return new KpiComparison(
                kpi01,
                kpi01.map(baseline -> registrantNames.apply(baseline.registeredBy())),
                kpi02,
                kpi02.map(baseline -> registrantNames.apply(baseline.registeredBy())),
                weeklyQueryService.pilotSummary(now, weeks),
                publicationCounter.count());
    }
}
