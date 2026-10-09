package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.List;
import java.util.Objects;

/**
 * 予定。追跡を開始したときに採用した、確定した経路版の区間の列（T-INV-10・T-INV-12。Bolt 25）。経路版は経路設計の業務番号（案件番号）と
 * 経路版番号で参照する。区間は 1 件以上で、前の区間の揚地は次の区間の積地と同じ、次の区間は前の区間の到着予定より前に出発しない。
 * 経路版は経路設計で検査済みだが、追跡の予定の規則として追跡の側でも守る。予定は実績と混同しない（T-INV-08）。
 *
 * @param routingCaseNumber 経路版の案件番号の表記
 * @param routeVersionNo 経路版番号（1 から）
 * @param legs 区間の列（区間番号は列の順に 1 から）
 */
@ValueObject
public record Schedule(String routingCaseNumber, int routeVersionNo, List<ScheduledLeg> legs) {

    public Schedule {
        Objects.requireNonNull(routingCaseNumber, "routingCaseNumber");
        Objects.requireNonNull(legs, "legs");
        if (routeVersionNo < 1) {
            throw new IllegalArgumentException("経路版番号は 1 から: " + routeVersionNo);
        }
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("予定には区間が 1 件以上要ります: " + routingCaseNumber);
        }
        legs = List.copyOf(legs);
        for (int i = 1; i < legs.size(); i++) {
            ScheduledLeg previous = legs.get(i - 1);
            ScheduledLeg next = legs.get(i);
            if (!previous.discharge().equals(next.load())) {
                throw new IllegalArgumentException("区間がつながりません: " + previous.voyageNumber() + " の揚地 "
                        + previous.discharge().unLocode() + "、" + next.voyageNumber() + " の積地 "
                        + next.load().unLocode());
            }
            if (next.departureAt().instant().isBefore(previous.arrivalAt().instant())) {
                throw new IllegalArgumentException("前の区間の到着予定より前に出発します: " + next.voyageNumber());
            }
        }
    }

    /** 最後の区間の到着予定（当初の到着予定。T-INV-10）。 */
    public UtcInstant finalArrival() {
        return legs.getLast().arrivalAt();
    }
}
