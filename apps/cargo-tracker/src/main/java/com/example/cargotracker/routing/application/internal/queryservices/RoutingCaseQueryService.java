package com.example.cargotracker.routing.application.internal.queryservices;

import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.aggregates.VoyageRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.rules.InfoFreshnessPolicy;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.routing.domain.model.valueobjects.StaleLeg;
import com.example.cargotracker.shared.domain.AuthenticatedActor;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 経路設計案件の照会（S-05・S-06。経路設計者が使う。Bolt 17）。社内の照会は操作者の担当企業で絞る（{@link RoutingCaseScope}。
 * Bolt 38g。US-16 AC4）。確定した区間の照会（経路設計の公開 API が委ねる）は主体を持たないので絞らない。
 *
 * <p>{@code @Service} は JIG がユースケースとして読むための印で、部品探索の対象にはしない（CargoTrackerApplication）。
 * 組み立ては {@code RoutingConfiguration} が担う。
 */
@Service
public class RoutingCaseQueryService {

    private final RoutingCaseRepository repository;
    private final RoutingCaseScope scope;
    private final VoyageRepository voyages;
    private final InfoFreshnessPolicy freshness;

    public RoutingCaseQueryService(
            RoutingCaseRepository repository,
            RoutingCaseScope scope,
            VoyageRepository voyages,
            InfoFreshnessPolicy freshness) {
        this.repository = repository;
        this.scope = scope;
        this.voyages = voyages;
        this.freshness = freshness;
    }

    /**
     * 算出の後に古くなった区間（S-06。経路設計者が案件を開いた時刻の航海で判定する。UC-06 4a。Bolt 39f2）。担当範囲を確かめた案件にだけ呼ぶ。候補の航海を 1 文だけ読み（N+1 にしない）、
     * 確定できない状態や候補がないときは読まない（PERF-01）。
     *
     * @param routingCase 担当範囲の中の案件
     * @param now 判定時刻（画面の時刻と同じ値）
     * @return 候補番号と区間の順の古くなった区間（なければ空）
     */
    public List<StaleLeg> staleLegsOf(RoutingCase routingCase, Instant now) {
        List<String> voyageNumbers = routingCase.candidateVoyageNumbers();
        if (!routingCase.confirmable() || voyageNumbers.isEmpty()) {
            return List.of();
        }
        return routingCase.staleLegs(voyages.findByVoyageNumbers(voyageNumbers), freshness, new UtcInstant(now));
    }

    /** 案件一覧（S-05）。操作者の担当企業の案件だけを、見積有効期限の近い順に返す（担当企業がなければ空）。 */
    public List<RoutingCaseSummary> listCases(AuthenticatedActor actor) {
        return scope.listWithin(actor, repository::findSummaries);
    }

    /** 担当範囲の中の案件と、いまの経路版の候補（S-06・S-07）。ない・範囲の外は空（どちらも 404。記録しない）。 */
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number, AuthenticatedActor actor) {
        return scope.find(number, actor).inScope();
    }

    /**
     * 案件が操作者の担当範囲の外か（状態を変える POST の前の確かめ。ロックしない。Bolt 38g）。ない案件は範囲の外ではない（記録せず
     * 404 はコマンドが返す）。
     */
    public boolean outOfScope(RoutingCaseNumber number, AuthenticatedActor actor) {
        return scope.find(number, actor) instanceof RoutingCaseLookup.OutOfScope;
    }

    /**
     * 確定した経路版の区間を区間の順に返す（経路設計の公開 API が委ねる。追跡の予定。ADR-015。Bolt 25）。
     *
     * @return 区間の列。案件・経路版がない、または確定していない経路版なら空
     */
    public List<Leg> confirmedLegs(RoutingCaseNumber number, int routeVersionNo) {
        return repository
                .findByNumber(number)
                .flatMap(routingCase -> routingCase.confirmedCandidateOf(routeVersionNo))
                .map(RouteCandidate::legs)
                .orElse(List.of());
    }
}
