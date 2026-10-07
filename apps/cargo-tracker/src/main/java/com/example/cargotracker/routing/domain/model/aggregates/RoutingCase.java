package com.example.cargotracker.routing.domain.model.aggregates;

import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.rules.ConstraintEvaluator;
import com.example.cargotracker.routing.domain.model.rules.RouteCandidateFinder;
import com.example.cargotracker.routing.domain.model.valueobjects.CandidateCalculation;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.shared.annotation.ddd.AggregateRoot;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 経路設計案件。1 つの輸送要求について経路版を作り、確定・再設計する単位（集約ルート）。
 * 荷主の詳細経路設計の依頼（DE-16）を受けて、輸送要求版ごとに 1 つ作る（R-INV-10）。Bolt 17 は経路版 1 の候補の算出まで。
 *
 * <p>輸送要求・見積りの ID と業務番号は、見積りの公開 API とイベントから得た値の写し（見積りのドメインの型を持たない。ADR-001）。
 */
@AggregateRoot
public final class RoutingCase {

    /** 示す候補の上限（Bolt 17 の確認ポイント 8）。 */
    public static final int MAX_CANDIDATES = 20;

    private static final long INITIAL_AGGREGATE_VERSION = 0;

    private final RoutingCaseId id;
    private final RoutingCaseNumber number;
    private final UUID transportRequestId;
    private final String transportRequestNumber;
    private final int transportRequestVersionNo;
    private final UUID quotationId;
    private final List<Location> routePolicyVia;
    private final RouteSpecification specification;
    private final UtcInstant requestedAt;
    private final long aggregateVersion;
    private RouteVersion routeVersion;

    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    private RoutingCase(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt,
            RouteVersion routeVersion,
            long aggregateVersion) {
        this.id = Objects.requireNonNull(id, "id");
        this.number = Objects.requireNonNull(number, "number");
        this.transportRequestId = Objects.requireNonNull(transportRequestId, "transportRequestId");
        this.transportRequestNumber = Objects.requireNonNull(transportRequestNumber, "transportRequestNumber");
        if (transportRequestVersionNo < 1) {
            throw new IllegalArgumentException("版番号は 1 以上です: " + transportRequestVersionNo);
        }
        this.transportRequestVersionNo = transportRequestVersionNo;
        this.quotationId = Objects.requireNonNull(quotationId, "quotationId");
        this.routePolicyVia = List.copyOf(routePolicyVia);
        this.specification = Objects.requireNonNull(specification, "specification");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.routeVersion = Objects.requireNonNull(routeVersion, "routeVersion");
        this.aggregateVersion = aggregateVersion;
    }

    /**
     * 詳細経路設計の依頼を受けて案件を作る（経路版 1 は作成中）。同じ輸送要求版の案件がないことは、アプリケーションサービスが
     * 確かめ、リポジトリの一意制約が最後に守る（R-INV-10）。
     */
    @SuppressWarnings("java:S107") // 依頼と経路条件の値をすべて受け取る
    public static RoutingCase open(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt) {
        return new RoutingCase(
                id,
                number,
                transportRequestId,
                transportRequestNumber,
                transportRequestVersionNo,
                quotationId,
                routePolicyVia,
                specification,
                requestedAt,
                RouteVersion.draft(1),
                INITIAL_AGGREGATE_VERSION);
    }

    /** 保存されている状態から案件を組み立てる（リポジトリが使う）。 */
    @SuppressWarnings("java:S107") // 保存されている状態から組み立てるため、集約の値をすべて受け取る
    public static RoutingCase reconstitute(
            RoutingCaseId id,
            RoutingCaseNumber number,
            UUID transportRequestId,
            String transportRequestNumber,
            int transportRequestVersionNo,
            UUID quotationId,
            List<Location> routePolicyVia,
            RouteSpecification specification,
            UtcInstant requestedAt,
            RouteVersion routeVersion,
            long aggregateVersion) {
        return new RoutingCase(
                id,
                number,
                transportRequestId,
                transportRequestNumber,
                transportRequestVersionNo,
                quotationId,
                routePolicyVia,
                specification,
                requestedAt,
                routeVersion,
                aggregateVersion);
    }

    /**
     * 候補を算出する（作成中・候補提示済み → 候補提示済み。US-06 AC1）。航海の一覧から区間の列を列挙し（候補探索）、
     * それぞれを判定時刻で判定して（制約適合判定）、適合を先に、到着予定の早い順に上限まで残し、1 から候補番号を振る。
     * 候補提示済みで算出し直したら、候補を置き換え、判定時刻を更新する。
     *
     * @throws IllegalStateException 作成中・候補提示済みでない（確定の後の再算出は US-08 の再設計で行う）
     */
    public CandidateCalculation calculateCandidates(
            List<Voyage> voyages,
            List<ConnectionRule> rules,
            UtcInstant judgedAt,
            RouteCandidateFinder finder,
            ConstraintEvaluator evaluator) {
        RouteVersionStatus status = routeVersion.status();
        if (status != RouteVersionStatus.DRAFT && status != RouteVersionStatus.CANDIDATES_PRESENTED) {
            throw new IllegalStateException("候補を算出できない経路版の状態です: " + status);
        }
        List<Evaluated> evaluated = finder.find(specification, voyages, judgedAt).stream()
                .map(legs -> new Evaluated(legs, evaluator.evaluate(specification, legs, rules, judgedAt)))
                .sorted(PRESENTATION_ORDER)
                .toList();
        List<RouteCandidate> candidates = new ArrayList<>();
        for (Evaluated candidate : evaluated.subList(0, Math.min(MAX_CANDIDATES, evaluated.size()))) {
            candidates.add(
                    new RouteCandidate(candidates.size() + 1, candidate.legs(), candidate.evaluation(), judgedAt));
        }
        routeVersion = new RouteVersion(
                routeVersion.routeVersionNo(), RouteVersionStatus.CANDIDATES_PRESENTED, candidates, judgedAt);
        int conforming = (int) candidates.stream()
                .filter(candidate -> candidate.evaluation().conforming())
                .count();
        return new CandidateCalculation(evaluated.size(), candidates.size(), conforming, judgedAt);
    }

    /** 判定した区間の列（候補番号を振る前）。 */
    private record Evaluated(List<Leg> legs, ConstraintEvaluation evaluation) {}

    /** 示す順。適合を先に、到着予定の早い順、同時刻は航海番号の順（確定的にする）。 */
    private static final Comparator<Evaluated> PRESENTATION_ORDER = Comparator.comparing(
                    (Evaluated candidate) -> !candidate.evaluation().conforming())
            .thenComparing(
                    candidate -> candidate.evaluation().estimatedArrivalAt().instant())
            .thenComparing(candidate -> String.join(
                    ",", candidate.legs().stream().map(Leg::voyageNumber).toList()));

    public RoutingCaseId id() {
        return id;
    }

    public RoutingCaseNumber number() {
        return number;
    }

    public UUID transportRequestId() {
        return transportRequestId;
    }

    public String transportRequestNumber() {
        return transportRequestNumber;
    }

    public int transportRequestVersionNo() {
        return transportRequestVersionNo;
    }

    public UUID quotationId() {
        return quotationId;
    }

    /** 経路方針（参考）の主な経由地。判定には使わない。 */
    public List<Location> routePolicyVia() {
        return routePolicyVia;
    }

    public RouteSpecification specification() {
        return specification;
    }

    public UtcInstant requestedAt() {
        return requestedAt;
    }

    /** いまの経路版（Bolt 17 は経路版 1 だけ）。 */
    public RouteVersion routeVersion() {
        return routeVersion;
    }

    public long aggregateVersion() {
        return aggregateVersion;
    }
}
