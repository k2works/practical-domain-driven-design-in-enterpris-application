package com.example.cargotracker.routing.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Objects;
import java.util.UUID;

/**
 * 確定の記録。経路版を確定したときの、選んだ候補、判断根拠、承認者、承認 commit 時刻（US-07 AC1。Bolt 19）。
 *
 * @param candidateNo 選んだ候補番号
 * @param rationale 判断根拠
 * @param approvedBy 承認者（経路設計者）の利用者 ID
 * @param approvedAt 承認 commit 時刻
 */
@ValueObject
public record RouteConfirmation(int candidateNo, DecisionRationale rationale, UUID approvedBy, UtcInstant approvedAt) {

    public RouteConfirmation {
        Objects.requireNonNull(rationale, "rationale");
        Objects.requireNonNull(approvedBy, "approvedBy");
        Objects.requireNonNull(approvedAt, "approvedAt");
    }
}
