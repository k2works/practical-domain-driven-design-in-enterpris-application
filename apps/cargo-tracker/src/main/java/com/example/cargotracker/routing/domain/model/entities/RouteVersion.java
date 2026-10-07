package com.example.cargotracker.routing.domain.model.entities;

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
 * Bolt 17 は作成中と候補提示済み、Bolt 19 で確定（選んだ候補・判断根拠・承認者・承認 commit 時刻）を足した。
 *
 * @param routeVersionNo 経路版番号（案件の中で 1 から）
 * @param status 状態
 * @param candidates 経路候補（候補番号の順）
 * @param candidatesEvaluatedAt 候補の判定時刻（作成中は null）
 * @param routeConfirmation 確定の記録（確定のときだけ。ほかは null）
 */
@Entity
public record RouteVersion(
        int routeVersionNo,
        RouteVersionStatus status,
        List<RouteCandidate> candidates,
        UtcInstant candidatesEvaluatedAt,
        RouteConfirmation routeConfirmation) {

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
        requireConsistentConfirmation(status, candidates, routeConfirmation);
    }

    /** 確定していない経路版。 */
    public RouteVersion(
            int routeVersionNo,
            RouteVersionStatus status,
            List<RouteCandidate> candidates,
            UtcInstant candidatesEvaluatedAt) {
        this(routeVersionNo, status, candidates, candidatesEvaluatedAt, null);
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

    /** 確定の記録（確定のときだけ）。 */
    public Optional<RouteConfirmation> confirmation() {
        return Optional.ofNullable(routeConfirmation);
    }

    /** 候補の判定時刻。 */
    public Optional<UtcInstant> evaluatedAt() {
        return Optional.ofNullable(candidatesEvaluatedAt);
    }
}
