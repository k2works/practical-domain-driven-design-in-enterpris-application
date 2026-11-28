package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * 予定の時点の列（予定の順。Bolt 39g4）。区間ごとに出発（積地・出発予定）と、最後の区間でなければ積替、最後の区間なら到着（揚地・到着予定）を
     * この順に並べる。積替予定と次の区間の出発予定が同じ時刻でも積替が先。
     */
    public List<SchedulePoint> points() {
        int last = legs.size() - 1;
        List<SchedulePoint> points = new ArrayList<>();
        for (int index = 0; index <= last; index++) {
            ScheduledLeg leg = legs.get(index);
            points.add(new SchedulePoint(MilestoneKind.DEPARTURE, leg.load(), leg.departureAt(), index));
            MilestoneKind arrivalKind = index == last ? MilestoneKind.ARRIVAL : MilestoneKind.TRANSSHIPMENT;
            points.add(new SchedulePoint(arrivalKind, leg.discharge(), leg.arrivalAt(), index));
        }
        return List.copyOf(points);
    }

    /**
     * 実績を結ぶ予定の時点（Bolt 39g3 の結び方を Bolt 39g4 で予定に出した。【業務の判断】(ao)）。種類と場所が同じ時点がちょうど 1 つならその時点。
     * 集荷・搬入・引渡しは予定に時刻がないので結ばない。場所が区間にない、種類と区間の位置が合わない（到着は最後の区間の揚地だけ、積替は最後の
     * 区間でない揚地だけ）、同じ場所が 2 つ以上の区間に当たる（同じ港を 2 度使う予定）実績は結ばない。
     *
     * @param kind 実績の種類
     * @param location 実績の場所
     * @return 結ぶ時点（結ばなければ空）
     */
    public Optional<SchedulePoint> pointOf(MilestoneKind kind, Location location) {
        List<SchedulePoint> matched = points().stream()
                .filter(point -> point.kind() == kind && point.location().equals(location))
                .toList();
        return matched.size() == 1 ? Optional.of(matched.getFirst()) : Optional.empty();
    }
}
