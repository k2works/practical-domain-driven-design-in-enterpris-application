package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateRoutingCaseException;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseId;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCaseSummary;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 経路設計案件のリポジトリの MyBatis 実装（Bolt 17）。案件・経路版・候補・区間・除外理由の表を組み立てて集約にする。
 * 更新は楽観ロック（集約の版）で照合し、いまの経路版の状態を書き直し、候補・区間・除外理由は消して入れ直す（候補は追記専用ではない）。
 *
 * <p>除外理由の閾値は 1 つの列に文字で持つ。期限超過は希望到着期限（ISO-8601 の UTC の時点）、接続不足は港と必要最小接続時間
 * （例: {@code SGSIN PT8H}）、接続できないは港。経路方針の経由地は UN/LOCODE のカンマ区切り（見積りと同じ）。
 */
@Repository
public class MyBatisRoutingCaseRepository implements RoutingCaseRepository {

    private static final String VIA_SEPARATOR = ",";
    private static final String THRESHOLD_SEPARATOR = " ";

    private final RoutingCaseMapper mapper;
    private final Clock clock;

    public MyBatisRoutingCaseRepository(RoutingCaseMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * 新しい案件を保存する。同じ輸送要求版の案件の UK に違反したら、セーブポイントに戻してドメインの例外にする
     * （PostgreSQL は制約違反でトランザクションを中断するため。見積りの保存と同じ）。
     */
    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void save(RoutingCase routingCase) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            mapper.insertRoutingCase(toRow(routingCase, now));
        } catch (DuplicateKeyException _) {
            throw new DuplicateRoutingCaseException(
                    routingCase.transportRequestId(), routingCase.transportRequestVersionNo());
        }
        RouteVersion version = routingCase.routeVersion();
        mapper.insertRouteVersion(toRow(routingCase.id().value(), version, now));
        insertCandidates(routingCase.id().value(), version);
    }

    @Override
    public void update(RoutingCase routingCase) {
        UUID id = routingCase.id().value();
        if (mapper.touchRoutingCase(id, routingCase.aggregateVersion(), OffsetDateTime.now(clock)) == 0) {
            throw new ConcurrentRoutingCaseUpdateException(routingCase.number(), routingCase.aggregateVersion());
        }
        RouteVersion version = routingCase.routeVersion();
        mapper.updateRouteVersion(toRow(id, version, null));
        mapper.deleteExclusionReasons(id, version.routeVersionNo());
        mapper.deleteLegs(id, version.routeVersionNo());
        mapper.deleteCandidates(id, version.routeVersionNo());
        insertCandidates(id, version);
    }

    @Override
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
        return mapper.selectByNumber(number.text()).map(this::toAggregate);
    }

    @Override
    public boolean existsForTransportRequestVersion(UUID transportRequestId, int transportRequestVersionNo) {
        return mapper.countByTransportRequestVersion(transportRequestId, transportRequestVersionNo) > 0;
    }

    @Override
    public List<RoutingCaseSummary> findSummaries() {
        return mapper.selectSummaries().stream()
                .map(row -> new RoutingCaseSummary(
                        RoutingCaseNumber.parse(row.caseNumber()),
                        row.transportRequestNumber(),
                        row.transportRequestVersionNo(),
                        new Location(row.originUnlocode()),
                        new Location(row.destinationUnlocode()),
                        toUtc(row.arrivalDeadline()),
                        toUtc(row.requestedAt()),
                        RouteVersionStatus.valueOf(row.status())))
                .toList();
    }

    private void insertCandidates(UUID routingCaseId, RouteVersion version) {
        int versionNo = version.routeVersionNo();
        for (RouteCandidate candidate : version.candidates()) {
            ConstraintEvaluation evaluation = candidate.evaluation();
            mapper.insertCandidate(new RouteCandidateRow(
                    routingCaseId,
                    versionNo,
                    candidate.candidateNo(),
                    evaluation.conforming(),
                    toOffset(evaluation.estimatedArrivalAt()),
                    evaluation
                            .connectionSlack()
                            .map(slack -> (int) slack.toMinutes())
                            .orElse(null),
                    toOffset(candidate.evaluatedAt()),
                    toOffset(candidate.oldestInfoAcquiredAt()),
                    false));
            for (int i = 0; i < candidate.legs().size(); i++) {
                Leg leg = candidate.legs().get(i);
                mapper.insertLeg(new CandidateLegRow(
                        routingCaseId,
                        versionNo,
                        candidate.candidateNo(),
                        i + 1,
                        leg.voyageNumber(),
                        leg.load().unLocode(),
                        leg.discharge().unLocode(),
                        toOffset(leg.departureAt()),
                        toOffset(leg.arrivalAt()),
                        leg.infoVersion(),
                        toOffset(leg.infoAcquiredAt()),
                        false));
            }
            for (int i = 0; i < evaluation.reasons().size(); i++) {
                ExclusionReason reason = evaluation.reasons().get(i);
                mapper.insertExclusionReason(new ExclusionReasonRow(
                        routingCaseId,
                        versionNo,
                        candidate.candidateNo(),
                        i + 1,
                        reason.code().name(),
                        toOffset(reason.violatedAt()),
                        threshold(reason),
                        reason.infoVersion()));
            }
        }
    }

    private RoutingCase toAggregate(RoutingCaseRow row) {
        UUID id = row.id();
        RouteVersionRow versionRow = mapper.selectRouteVersions(id).getLast();
        Map<Integer, List<CandidateLegRow>> legs = mapper.selectLegs(id).stream()
                .filter(leg -> leg.routeVersionNo() == versionRow.routeVersionNo())
                .collect(Collectors.groupingBy(CandidateLegRow::candidateNo));
        Map<Integer, List<ExclusionReasonRow>> reasons = mapper.selectExclusionReasons(id).stream()
                .filter(reason -> reason.routeVersionNo() == versionRow.routeVersionNo())
                .collect(Collectors.groupingBy(ExclusionReasonRow::candidateNo));
        List<RouteCandidate> candidates = mapper.selectCandidates(id).stream()
                .filter(candidate -> candidate.routeVersionNo() == versionRow.routeVersionNo())
                .map(candidate -> toCandidate(
                        candidate,
                        legs.getOrDefault(candidate.candidateNo(), List.of()),
                        reasons.getOrDefault(candidate.candidateNo(), List.of())))
                .toList();
        RouteVersion version = new RouteVersion(
                versionRow.routeVersionNo(),
                RouteVersionStatus.valueOf(versionRow.status()),
                candidates,
                versionRow.candidatesEvaluatedAt() == null ? null : toUtc(versionRow.candidatesEvaluatedAt()));
        return RoutingCase.reconstitute(
                new RoutingCaseId(id),
                RoutingCaseNumber.parse(row.caseNumber()),
                row.transportRequestId(),
                row.transportRequestNumber(),
                row.transportRequestVersionNo(),
                row.quotationId(),
                row.routePolicyVia() == null || row.routePolicyVia().isEmpty()
                        ? List.of()
                        : Arrays.stream(row.routePolicyVia().split(VIA_SEPARATOR))
                                .map(Location::new)
                                .toList(),
                new RouteSpecification(
                        new Location(row.originUnlocode()),
                        new Location(row.destinationUnlocode()),
                        toUtc(row.arrivalDeadline()),
                        row.cargoCategory()),
                toUtc(row.requestedAt()),
                version,
                row.version());
    }

    private static RouteCandidate toCandidate(
            RouteCandidateRow row, List<CandidateLegRow> legs, List<ExclusionReasonRow> reasons) {
        return new RouteCandidate(
                row.candidateNo(),
                legs.stream()
                        .map(leg -> new Leg(
                                leg.voyageNumber(),
                                new Location(leg.loadUnlocode()),
                                new Location(leg.dischargeUnlocode()),
                                toUtc(leg.departureAt()),
                                toUtc(leg.arrivalAt()),
                                leg.infoVersion(),
                                toUtc(leg.infoAcquiredAt())))
                        .toList(),
                new ConstraintEvaluation(
                        toUtc(row.estimatedArrivalAt()),
                        reasons.stream()
                                .map(MyBatisRoutingCaseRepository::toReason)
                                .toList(),
                        row.minConnectionSlackMinutes() == null
                                ? null
                                : Duration.ofMinutes(row.minConnectionSlackMinutes())),
                toUtc(row.evaluatedAt()));
    }

    private static String threshold(ExclusionReason reason) {
        return switch (reason.code()) {
            case DEADLINE_EXCEEDED -> reason.deadline().instant().toString();
            case CONNECTION_TOO_SHORT -> reason.port().unLocode() + THRESHOLD_SEPARATOR + reason.requiredConnection();
            case NOT_CONNECTABLE -> reason.port().unLocode();
            case CARGO_NOT_SUPPORTED, INFO_INSUFFICIENT ->
                throw new IllegalStateException("R0.1 では判定しない除外理由です: " + reason.code());
        };
    }

    private static ExclusionReason toReason(ExclusionReasonRow row) {
        ExclusionReasonCode code = ExclusionReasonCode.valueOf(row.reasonCode());
        UtcInstant violatedAt = toUtc(row.violatedAt());
        return switch (code) {
            case DEADLINE_EXCEEDED ->
                ExclusionReason.deadlineExceeded(
                        violatedAt, new UtcInstant(Instant.parse(row.threshold())), row.infoVersion());
            case CONNECTION_TOO_SHORT -> {
                String[] parts = row.threshold().split(THRESHOLD_SEPARATOR);
                yield ExclusionReason.connectionTooShort(
                        violatedAt, new Location(parts[0]), Duration.parse(parts[1]), row.infoVersion());
            }
            case NOT_CONNECTABLE ->
                ExclusionReason.notConnectable(violatedAt, new Location(row.threshold()), row.infoVersion());
            case CARGO_NOT_SUPPORTED, INFO_INSUFFICIENT ->
                throw new IllegalStateException("R0.1 では判定しない除外理由です: " + code);
        };
    }

    private static RoutingCaseRow toRow(RoutingCase routingCase, OffsetDateTime now) {
        RouteSpecification specification = routingCase.specification();
        return new RoutingCaseRow(
                routingCase.id().value(),
                routingCase.number().text(),
                routingCase.transportRequestId(),
                routingCase.transportRequestNumber(),
                routingCase.transportRequestVersionNo(),
                routingCase.quotationId(),
                routingCase.routePolicyVia().stream()
                        .map(Location::unLocode)
                        .collect(Collectors.joining(VIA_SEPARATOR)),
                specification.origin().unLocode(),
                specification.destination().unLocode(),
                toOffset(specification.arrivalDeadline()),
                specification.cargoCategory(),
                toOffset(routingCase.requestedAt()),
                routingCase.aggregateVersion(),
                now,
                now);
    }

    private static RouteVersionRow toRow(UUID routingCaseId, RouteVersion version, OffsetDateTime createdAt) {
        return new RouteVersionRow(
                routingCaseId,
                version.routeVersionNo(),
                version.status().name(),
                version.evaluatedAt()
                        .map(MyBatisRoutingCaseRepository::toOffset)
                        .orElse(null),
                createdAt);
    }

    private static OffsetDateTime toOffset(UtcInstant instant) {
        return instant.instant().atOffset(ZoneOffset.UTC);
    }

    private static UtcInstant toUtc(OffsetDateTime dateTime) {
        return new UtcInstant(dateTime.toInstant());
    }
}
