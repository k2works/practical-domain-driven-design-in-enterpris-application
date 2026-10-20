package com.example.cargotracker.quotation.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 割り当てた経路。経路設計が確定して見積りに割り当てた経路版の写し（R-INV-11、ADR-014。Bolt 20）。
 * 荷主の承認の画面は、経路設計に問い合わせずにこれを示す。経路版は案件番号と経路版番号で参照する（内部の ID を持たない。D-4）。
 *
 * @param routingCaseNumber 案件番号の表記（例: RC-2026-0001）
 * @param routeVersionNo 経路版番号（1 から）
 * @param confirmedAt 経路を確定した時刻（承認 commit 時刻）
 * @param legs 区間の写し（1 件以上。区間の順）
 */
@ValueObject
public record AssignedRoute(
        String routingCaseNumber, int routeVersionNo, UtcInstant confirmedAt, List<AssignedRouteLeg> legs) {

    public AssignedRoute {
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(confirmedAt, "confirmedAt");
        legs = List.copyOf(legs);
        if (routingCaseNumber.isBlank()) {
            throw new IllegalArgumentException("案件番号がありません");
        }
        if (routeVersionNo < 1) {
            throw new IllegalArgumentException("経路版番号は 1 以上です: " + routeVersionNo);
        }
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("区間がありません");
        }
    }

    /** 同じ経路版か（案件番号と経路版番号が同じ）。割当ての冪等の判定に使う。 */
    public boolean isSameVersionAs(AssignedRoute other) {
        return routingCaseNumber.equals(other.routingCaseNumber) && routeVersionNo == other.routeVersionNo;
    }

    /** 到着予定（最後の区間の到着予定）。 */
    public UtcInstant arrivalAt() {
        return legs.getLast().arrivalAt();
    }
}
