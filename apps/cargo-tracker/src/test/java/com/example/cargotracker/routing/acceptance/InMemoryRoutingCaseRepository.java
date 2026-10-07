package com.example.cargotracker.routing.acceptance;

import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateRoutingCaseException;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 業務ルール層の受入シナリオと単体テストで使う、メモリ上の経路設計案件のリポジトリ。本物と同じ規則で振る舞う。
 * <ul>
 *   <li>同じ輸送要求版の案件を 2 回保存すると失敗する（UK。R-INV-10）
 *   <li>読み出すたびに保存した時点の写しを返し、更新は読み込んだときの集約の版で照合する（楽観ロック）
 * </ul>
 */
public class InMemoryRoutingCaseRepository implements RoutingCaseRepository {

    private final Map<RoutingCaseNumber, RoutingCase> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(RoutingCase routingCase) {
        if (existsForTransportRequestVersion(
                routingCase.transportRequestId(), routingCase.transportRequestVersionNo())) {
            throw new DuplicateRoutingCaseException(
                    routingCase.transportRequestId(), routingCase.transportRequestVersionNo());
        }
        store.put(routingCase.number(), snapshot(routingCase, routingCase.aggregateVersion()));
    }

    @Override
    public synchronized void update(RoutingCase routingCase) {
        RoutingCase stored = store.get(routingCase.number());
        if (stored == null || stored.aggregateVersion() != routingCase.aggregateVersion()) {
            throw new ConcurrentRoutingCaseUpdateException(routingCase.number(), routingCase.aggregateVersion());
        }
        store.put(routingCase.number(), snapshot(routingCase, routingCase.aggregateVersion() + 1));
    }

    @Override
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
        return Optional.ofNullable(store.get(number)).map(found -> snapshot(found, found.aggregateVersion()));
    }

    @Override
    public boolean existsForTransportRequestVersion(UUID transportRequestId, int transportRequestVersionNo) {
        return store.values().stream()
                .anyMatch(stored -> stored.transportRequestId().equals(transportRequestId)
                        && stored.transportRequestVersionNo() == transportRequestVersionNo);
    }

    @Override
    public List<RoutingCaseSummary> findSummaries() {
        return store.values().stream()
                .sorted(Comparator.comparing(
                                (RoutingCase found) -> found.requestedAt().instant())
                        .thenComparing(found -> found.number().text())
                        .reversed())
                .map(found -> new RoutingCaseSummary(
                        found.number(),
                        found.transportRequestNumber(),
                        found.transportRequestVersionNo(),
                        found.specification().origin(),
                        found.specification().destination(),
                        found.specification().arrivalDeadline(),
                        found.requestedAt(),
                        found.routeVersion().status()))
                .toList();
    }

    /** 保存した案件の数。 */
    public int count() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }

    private static RoutingCase snapshot(RoutingCase source, long version) {
        return RoutingCase.reconstitute(
                source.id(),
                source.number(),
                source.transportRequestId(),
                source.transportRequestNumber(),
                source.transportRequestVersionNo(),
                source.quotationId(),
                source.routePolicyVia(),
                source.specification(),
                source.requestedAt(),
                source.routeVersions(),
                version);
    }
}
