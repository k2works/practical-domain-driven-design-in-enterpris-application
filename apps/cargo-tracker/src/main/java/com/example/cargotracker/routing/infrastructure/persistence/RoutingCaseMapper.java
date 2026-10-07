package com.example.cargotracker.routing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 経路設計案件・経路版・候補・区間・除外理由の表の MyBatis マッパー。
 * SQL は同じパッケージの RoutingCaseMapper.xml に置き、H2 と PostgreSQL の共通の構文で書く（ADR-007）。
 */
@Mapper
public interface RoutingCaseMapper {

    void insertRoutingCase(RoutingCaseRow row);

    void insertRouteVersion(RouteVersionRow row);

    void insertCandidate(RouteCandidateRow row);

    void insertLeg(CandidateLegRow row);

    void insertExclusionReason(ExclusionReasonRow row);

    /** 楽観ロックで案件の版を進める。読み込んだときの版でなければ 0 件。 */
    int touchRoutingCase(
            @Param("id") UUID id,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") java.time.OffsetDateTime updatedAt);

    void updateRouteVersion(RouteVersionRow row);

    /** 経路版の候補・区間・除外理由を消す（再算出で入れ直すため）。 */
    void deleteExclusionReasons(
            @Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    void deleteLegs(@Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    void deleteCandidates(@Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    Optional<RoutingCaseRow> selectByNumber(String caseNumber);

    int countByTransportRequestVersion(
            @Param("transportRequestId") UUID transportRequestId,
            @Param("transportRequestVersionNo") int transportRequestVersionNo);

    List<RouteVersionRow> selectRouteVersions(UUID routingCaseId);

    List<RouteCandidateRow> selectCandidates(UUID routingCaseId);

    List<CandidateLegRow> selectLegs(UUID routingCaseId);

    List<ExclusionReasonRow> selectExclusionReasons(UUID routingCaseId);

    List<RoutingCaseSummaryRow> selectSummaries();
}
