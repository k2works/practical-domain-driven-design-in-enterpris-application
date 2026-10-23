package com.example.cargotracker.routing.infrastructure.persistence;

import com.example.cargotracker.routing.domain.model.aggregates.ConcurrentRoutingCaseUpdateException;
import com.example.cargotracker.routing.domain.model.aggregates.DuplicateRoutingCaseException;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCase;
import com.example.cargotracker.routing.domain.model.aggregates.RoutingCaseRepository;
import com.example.cargotracker.routing.domain.model.entities.RouteCandidate;
import com.example.cargotracker.routing.domain.model.entities.RouteVersion;
import com.example.cargotracker.routing.domain.model.valueobjects.ConstraintEvaluation;
import com.example.cargotracker.routing.domain.model.valueobjects.DecisionRationale;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReason;
import com.example.cargotracker.routing.domain.model.valueobjects.ExclusionReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignReasonCode;
import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
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
 * 確定した経路版は書き直さず、確定するときは状態と確定の記録だけを書く（Bolt 19。R-INV-06）。
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
    public void update(RoutingCase routingCase, UUID operatorId) {
        UUID id = routingCase.id().value();
        if (mapper.touchRoutingCase(id, routingCase.aggregateVersion(), OffsetDateTime.now(clock), operatorId) == 0) {
            throw new ConcurrentRoutingCaseUpdateException(routingCase.number(), routingCase.aggregateVersion());
        }
        RouteVersion version = routingCase.routeVersion();
        if (version.status() == RouteVersionStatus.REDESIGN_REQUIRED) {
            // 確定から再設計要に変わるときは、状態と再設計要の記録だけを書く（候補と確定の記録は書き直さない。旧情報版の保持。Bolt 37d）。
            // 表の条件（確定の行だけ）は最後の守り。書けなければ（確定でない行、すでに再設計要の行）例外にする
            if (mapper.updateRouteVersionToRedesignRequired(toRow(id, version, null)) == 0) {
                throw new IllegalStateException(
                        "確定でない経路版は再設計要にできない: " + routingCase.number().text() + " 版 " + version.routeVersionNo());
            }
            return;
        }
        int updated = mapper.updateRouteVersion(toRow(id, version, null));
        // 確定した経路版の候補は書き直さない（R-INV-06）。確定するときは状態と確定の記録だけを書き（1 件）、確定の後の更新では
        // 表の条件（作成中・候補提示済みだけ）で何も書かない（0 件）。表の条件は最後の守りで、分岐はドメインの状態で決める
        if (version.status() == RouteVersionStatus.CONFIRMED) {
            return;
        }
        if (updated == 0) {
            throw new IllegalStateException("経路版を書き直せない: "
                    + routingCase.number().text() + " 版 " + version.routeVersionNo() + " " + version.status());
        }
        mapper.deleteExclusionReasons(id, version.routeVersionNo());
        mapper.deleteLegs(id, version.routeVersionNo());
        mapper.deleteCandidates(id, version.routeVersionNo());
        insertCandidates(id, version);
    }

    @Override
    public Optional<RoutingCase> findByNumber(RoutingCaseNumber number) {
        return mapper.selectByNumber(number.text()).map(this::toAggregate);
    }

    // ロックを呼び手のトランザクションの終わりまで持つ。外で呼ぶと文の終わりで外れるので例外にする（Bolt 37e2 の開発レビュー）
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public Optional<RoutingCase> findByNumberForUpdate(RoutingCaseNumber number) {
        // 行を押さえてから組み立てる（押さえる前に読んだ値をキャッシュから使わない）
        if (mapper.lockByNumber(number.text()) == null) {
            return Optional.empty();
        }
        return findByNumber(number);
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
                        RouteVersionStatus.valueOf(row.status()),
                        row.quotationExpiresAt() == null ? null : toUtc(row.quotationExpiresAt())))
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
                    evaluation.infoInsufficient()));
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
        List<CandidateLegRow> legs = mapper.selectLegs(id);
        List<ExclusionReasonRow> reasons = mapper.selectExclusionReasons(id);
        List<RouteCandidateRow> candidates = mapper.selectCandidates(id);
        List<RouteVersion> versions = mapper.selectRouteVersions(id).stream()
                .map(versionRow -> toVersion(versionRow, candidates, legs, reasons))
                .toList();
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
                row.quotationExpiresAt() == null ? null : toUtc(row.quotationExpiresAt()),
                row.createdBy(),
                versions,
                row.version());
    }

    private static RouteVersion toVersion(
            RouteVersionRow versionRow,
            List<RouteCandidateRow> candidateRows,
            List<CandidateLegRow> legRows,
            List<ExclusionReasonRow> reasonRows) {
        int versionNo = versionRow.routeVersionNo();
        Map<Integer, List<CandidateLegRow>> legs = legRows.stream()
                .filter(leg -> leg.routeVersionNo() == versionNo)
                .collect(Collectors.groupingBy(CandidateLegRow::candidateNo));
        Map<Integer, List<ExclusionReasonRow>> reasons = reasonRows.stream()
                .filter(reason -> reason.routeVersionNo() == versionNo)
                .collect(Collectors.groupingBy(ExclusionReasonRow::candidateNo));
        List<RouteCandidate> candidates = candidateRows.stream()
                .filter(candidate -> candidate.routeVersionNo() == versionNo)
                .map(candidate -> toCandidate(
                        candidate,
                        legs.getOrDefault(candidate.candidateNo(), List.of()),
                        reasons.getOrDefault(candidate.candidateNo(), List.of())))
                .toList();
        RouteConfirmation confirmation = versionRow.selectedCandidateNo() == null
                ? null
                : new RouteConfirmation(
                        versionRow.selectedCandidateNo(),
                        new DecisionRationale(versionRow.rationale()),
                        versionRow.approvedBy(),
                        toUtc(versionRow.approvedAt()));
        return new RouteVersion(
                versionNo,
                RouteVersionStatus.valueOf(versionRow.status()),
                candidates,
                versionRow.candidatesEvaluatedAt() == null ? null : toUtc(versionRow.candidatesEvaluatedAt()),
                versionRow.candidatesFound(),
                confirmation,
                toRedesign(versionRow));
    }

    /** 再設計要の記録（理由の区分は空白で区切る。Bolt 37d）。 */
    private static RedesignRecord toRedesign(RouteVersionRow row) {
        if (row.redesignRequiredAt() == null) {
            return null;
        }
        return new RedesignRecord(
                toUtc(row.redesignRequiredAt()),
                Arrays.stream(row.redesignReasons().split(THRESHOLD_SEPARATOR))
                        .map(RedesignReasonCode::valueOf)
                        .toList());
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
            case INFO_INSUFFICIENT -> reason.voyageNumber() + THRESHOLD_SEPARATOR + reason.freshnessTolerance();
            case CARGO_NOT_SUPPORTED -> throw new IllegalStateException("判定しない除外理由です: " + reason.code());
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
            case INFO_INSUFFICIENT -> {
                // 航海番号は空白を含み得るので、最後の空白で分ける（Bolt 37b2）
                int separator = row.threshold().lastIndexOf(THRESHOLD_SEPARATOR);
                if (separator < 0) {
                    throw new IllegalStateException("情報不足の閾値に区切りがありません: " + row.threshold());
                }
                Duration tolerance = Duration.parse(row.threshold().substring(separator + 1));
                yield ExclusionReason.infoInsufficient(
                        row.threshold().substring(0, separator),
                        new UtcInstant(violatedAt.instant().minus(tolerance)),
                        tolerance,
                        row.infoVersion());
            }
            case CARGO_NOT_SUPPORTED -> throw new IllegalStateException("判定しない除外理由です: " + code);
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
                routingCase
                        .quotationExpiresAt()
                        .map(MyBatisRoutingCaseRepository::toOffset)
                        .orElse(null),
                routingCase.requestedBy().orElse(null),
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
                version.candidatesFound(),
                version.confirmation().map(RouteConfirmation::candidateNo).orElse(null),
                version.confirmation()
                        .map(confirmation -> confirmation.rationale().text())
                        .orElse(null),
                version.confirmation().map(RouteConfirmation::approvedBy).orElse(null),
                version.confirmation()
                        .map(confirmation -> toOffset(confirmation.approvedAt()))
                        .orElse(null),
                version.redesign()
                        .map(redesign -> toOffset(redesign.requiredAt()))
                        .orElse(null),
                version.redesign()
                        .map(redesign -> redesign.reasons().stream()
                                .map(Enum::name)
                                .collect(Collectors.joining(THRESHOLD_SEPARATOR)))
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
