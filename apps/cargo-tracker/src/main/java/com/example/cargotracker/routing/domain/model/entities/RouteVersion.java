package com.example.cargotracker.routing.domain.model.entities;

import com.example.cargotracker.routing.domain.model.valueobjects.RedesignRecord;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteConfirmation;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteVersionStatus;
import com.example.cargotracker.shared.annotation.ddd.Entity;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 経路版。候補の比較、判断根拠、承認を伴って確定した経路のある版。経路設計案件の中で経路版番号で識別する。
 * Bolt 17 は作成中と候補提示済み、Bolt 19 で確定（選んだ候補・判断根拠・承認者・承認 commit 時刻）を足した。Bolt 37d で
 * 再設計要の記録（再設計要にした時刻と理由。US-07 AC4）を足した。
 *
 * @param routeVersionNo 経路版番号（案件の中で 1 から）
 * @param status 状態
 * @param candidates 経路候補（候補番号の順）
 * @param candidatesEvaluatedAt 候補の判定時刻（作成中は null）
 * @param candidatesFound 見つけた候補の数（上限で切る前。示す候補の数以上。Bolt 19。Bolt 17 レビュー D-64）
 * @param routeConfirmation 確定の記録（確定と、確定の後の再設計要・旧版。ほかは null）
 * @param redesignRecord 再設計要の記録（再設計要は必ず、旧版は任意。ほかは null。Bolt 37d）
 */
@Entity
public record RouteVersion(
        int routeVersionNo,
        RouteVersionStatus status,
        List<RouteCandidate> candidates,
        UtcInstant candidatesEvaluatedAt,
        int candidatesFound,
        RouteConfirmation routeConfirmation,
        RedesignRecord redesignRecord) {

    /** 再設計要の記録を持てる状態（再設計要と、再設計要の後の旧版。Bolt 37d）。 */
    private static final Set<RouteVersionStatus> CAN_HOLD_REDESIGN =
            EnumSet.of(RouteVersionStatus.REDESIGN_REQUIRED, RouteVersionStatus.SUPERSEDED);

    /** 確定の記録を持てる状態（確定と、確定の後の再設計要・旧版）。 */
    private static final Set<RouteVersionStatus> CAN_HOLD_CONFIRMATION = EnumSet.of(
            RouteVersionStatus.CONFIRMED, RouteVersionStatus.REDESIGN_REQUIRED, RouteVersionStatus.SUPERSEDED);

    public RouteVersion {
        if (routeVersionNo < 1) {
            throw new IllegalArgumentException("経路版番号は 1 以上です: " + routeVersionNo);
        }
        Objects.requireNonNull(status, "status");
        candidates = List.copyOf(candidates);
        if (status == RouteVersionStatus.DRAFT && (!candidates.isEmpty() || candidatesEvaluatedAt != null)) {
            throw new IllegalArgumentException("作成中の経路版は候補を持ちません");
        }
        if (status != RouteVersionStatus.DRAFT && candidatesEvaluatedAt == null) {
            throw new IllegalArgumentException("候補を算出した経路版には判定時刻が要ります");
        }
        if (candidatesFound < candidates.size()) {
            throw new IllegalArgumentException("見つけた候補の数は示す候補の数以上です: " + candidatesFound);
        }
        requireConsistentConfirmation(status, candidates, routeConfirmation);
        requireConsistentRedesign(status, routeConfirmation, redesignRecord);
    }

    /**
     * 再設計要の経路版は確定の記録と再設計要の記録を持ち、記録を持てるのは再設計要と旧版だけ（Bolt 37d。再設計要は確定した経路版が
     * 外部情報の更新で不適合になった状態なので、確定の記録を持つ）。
     */
    private static void requireConsistentRedesign(
            RouteVersionStatus status, RouteConfirmation routeConfirmation, RedesignRecord redesignRecord) {
        if (status == RouteVersionStatus.REDESIGN_REQUIRED && (redesignRecord == null || routeConfirmation == null)) {
            throw new IllegalArgumentException("再設計要の経路版には確定の記録と再設計要の記録が要ります");
        }
        if (redesignRecord != null && !CAN_HOLD_REDESIGN.contains(status)) {
            throw new IllegalArgumentException("再設計要の記録を持てない経路版の状態です: " + status);
        }
    }

    /** 再設計要の記録を持たない経路版（Bolt 37d より前の形）。 */
    public RouteVersion(
            int routeVersionNo,
            RouteVersionStatus status,
            List<RouteCandidate> candidates,
            UtcInstant candidatesEvaluatedAt,
            int candidatesFound,
            RouteConfirmation routeConfirmation) {
        this(routeVersionNo, status, candidates, candidatesEvaluatedAt, candidatesFound, routeConfirmation, null);
    }

    /** 確定の記録を持つ経路版（見つけた候補の数は示す候補の数）。 */
    public RouteVersion(
            int routeVersionNo,
            RouteVersionStatus status,
            List<RouteCandidate> candidates,
            UtcInstant candidatesEvaluatedAt,
            RouteConfirmation routeConfirmation) {
        this(routeVersionNo, status, candidates, candidatesEvaluatedAt, candidates.size(), routeConfirmation, null);
    }

    /** 確定していない経路版。 */
    public RouteVersion(
            int routeVersionNo,
            RouteVersionStatus status,
            List<RouteCandidate> candidates,
            UtcInstant candidatesEvaluatedAt) {
        this(routeVersionNo, status, candidates, candidatesEvaluatedAt, candidates.size(), null, null);
    }

    /** 確定の経路版は確定の記録を持ち、確定していない経路版は持たない。記録の候補は経路版にある。 */
    private static void requireConsistentConfirmation(
            RouteVersionStatus status, List<RouteCandidate> candidates, RouteConfirmation routeConfirmation) {
        if (status == RouteVersionStatus.CONFIRMED && routeConfirmation == null) {
            throw new IllegalArgumentException("確定の経路版には確定の記録が要ります");
        }
        if (routeConfirmation != null && !CAN_HOLD_CONFIRMATION.contains(status)) {
            throw new IllegalArgumentException("確定していない経路版は確定の記録を持ちません: " + status);
        }
        if (routeConfirmation != null
                && candidates.stream()
                        .noneMatch(candidate -> candidate.candidateNo() == routeConfirmation.candidateNo())) {
            throw new IllegalArgumentException("確定の記録の候補が経路版にありません: " + routeConfirmation.candidateNo());
        }
    }

    /** 作成中の経路版。 */
    public static RouteVersion draft(int routeVersionNo) {
        return new RouteVersion(routeVersionNo, RouteVersionStatus.DRAFT, List.of(), null);
    }

    /** 再設計要の記録（再設計要と、記録を持つ旧版。Bolt 37d）。 */
    public Optional<RedesignRecord> redesign() {
        return Optional.ofNullable(redesignRecord);
    }

    /** 確定の記録（確定と、確定の後の再設計要・旧版）。 */
    public Optional<RouteConfirmation> confirmation() {
        return Optional.ofNullable(routeConfirmation);
    }

    /** 上限で切って示さなかった候補の数（Bolt 17 レビュー D-64。算出の直後だけでなく常に示す）。 */
    public int omittedCandidates() {
        return candidatesFound - candidates.size();
    }

    /** 候補の判定時刻。 */
    public Optional<UtcInstant> evaluatedAt() {
        return Optional.ofNullable(candidatesEvaluatedAt);
    }
}
