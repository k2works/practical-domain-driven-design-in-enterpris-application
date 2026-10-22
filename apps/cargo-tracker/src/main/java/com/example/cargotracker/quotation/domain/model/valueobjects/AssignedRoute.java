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
 * @param redesignRequiredAt 経路設計がこの経路版を再設計要にした時刻（再設計要でなければ null。経路版の事実の写しで、見積りの状態ではない。
 *     再見積りは経路を写すので印も写る。US-07 AC5、Q-INV-10。Bolt 37e）
 */
@ValueObject
public record AssignedRoute(
        String routingCaseNumber,
        int routeVersionNo,
        UtcInstant confirmedAt,
        List<AssignedRouteLeg> legs,
        UtcInstant redesignRequiredAt) {

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

    /** 再設計要の印のない経路（経路設計が確定して割り当てたとき）。 */
    public AssignedRoute(
            String routingCaseNumber, int routeVersionNo, UtcInstant confirmedAt, List<AssignedRouteLeg> legs) {
        this(routingCaseNumber, routeVersionNo, confirmedAt, legs, null);
    }

    /** 再設計要の印があるか。 */
    public boolean redesignRequired() {
        return redesignRequiredAt != null;
    }

    /** 再設計要の印を付けた写し（すでに付いていれば最初の時刻のまま）。 */
    public AssignedRoute withRedesignRequiredAt(UtcInstant at) {
        Objects.requireNonNull(at, "at");
        return redesignRequired() ? this : new AssignedRoute(routingCaseNumber, routeVersionNo, confirmedAt, legs, at);
    }

    /** 同じ経路版か（案件番号と経路版番号が同じ）。割当てと印の冪等の判定に使う（印は見ない）。 */
    public boolean isSameVersionAs(AssignedRoute other) {
        return isVersion(other.routingCaseNumber, other.routeVersionNo);
    }

    /** 案件番号と経路版番号の経路版か。 */
    public boolean isVersion(String otherRoutingCaseNumber, int otherRouteVersionNo) {
        return routingCaseNumber.equals(otherRoutingCaseNumber) && routeVersionNo == otherRouteVersionNo;
    }

    /** 到着予定（最後の区間の到着予定）。 */
    public UtcInstant arrivalAt() {
        return legs.getLast().arrivalAt();
    }
}
