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

    /**
     * 楽観ロックで案件の版を進め、最終更新者を書く。読み込んだときの版でなければ 0 件（確定した経路版の番号は Bolt 37d で消した）。
     * 最終更新者が null なら書かない（システムの操作は最終更新者を変えない。null の型を H2 と PostgreSQL で解決させない。Bolt 37d2）。
     * 更新時刻はシステムの操作でも進むので、最終更新者と組で「その利用者がその時刻に更新した」とは読まない。
     */
    int touchRoutingCase(
            @Param("id") UUID id,
            @Param("expectedVersion") long expectedVersion,
            @Param("updatedAt") java.time.OffsetDateTime updatedAt,
            @Param("updatedBy") UUID updatedBy);

    /** 作成中・候補提示済みの経路版だけを書き直す（確定した経路版は書き直さない。R-INV-06）。書き直した件数を返す。 */
    int updateRouteVersion(RouteVersionRow row);

    /** 確定の経路版だけを再設計要にし、再設計要の記録を書く（候補・確定の記録は書き直さない。Bolt 37d）。書き直した件数を返す。 */
    int updateRouteVersionToRedesignRequired(RouteVersionRow row);

    /** 経路版の候補・区間・除外理由を消す（再算出で入れ直すため）。 */
    void deleteExclusionReasons(
            @Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    void deleteLegs(@Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    void deleteCandidates(@Param("routingCaseId") UUID routingCaseId, @Param("routeVersionNo") int routeVersionNo);

    Optional<RoutingCaseRow> selectByNumber(String caseNumber);

    /** 案件の行を押さえる（Bolt 37e2）。案件がなければ null。 */
    UUID lockByNumber(String caseNumber);

    int countByTransportRequestVersion(
            @Param("transportRequestId") UUID transportRequestId,
            @Param("transportRequestVersionNo") int transportRequestVersionNo);

    List<RouteVersionRow> selectRouteVersions(UUID routingCaseId);

    List<RouteCandidateRow> selectCandidates(UUID routingCaseId);

    List<CandidateLegRow> selectLegs(UUID routingCaseId);

    List<ExclusionReasonRow> selectExclusionReasons(UUID routingCaseId);

    List<RoutingCaseSummaryRow> selectSummaries();
}
