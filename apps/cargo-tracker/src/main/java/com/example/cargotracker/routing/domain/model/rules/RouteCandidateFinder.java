package com.example.cargotracker.routing.domain.model.rules;

import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.Leg;
import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 経路候補探索。航海の一覧から、出発地から目的地までの区間の列を列挙する（ドメインモデル）。
 * MVP は確定的な列挙とし、最適化はしない。R0.1 は直行と 1 回の積替え（区間 1〜2）まで（Bolt 17）。
 *
 * <p>区間は、航海の寄港のうち、出発予定のある寄港から、それより後の到着予定のある寄港までを取る。判定時刻より後に出発する区間だけを
 * 列挙する。積替えは、出発地・目的地でない港で、前の区間の到着予定以後に同じ港を出発する別の航海につなぐ。接続時間が足りるかは
 * 制約適合判定が決める（足りない候補も除外の理由とともに示すため、ここでは捨てない）。
 */
@DomainRule
public class RouteCandidateFinder {

    private static final Comparator<List<Leg>> BY_ARRIVAL_THEN_VOYAGES =
            Comparator.<List<Leg>, java.time.Instant>comparing(
                            legs -> legs.getLast().arrivalAt().instant())
                    .thenComparing(legs -> String.join(
                            ",", legs.stream().map(Leg::voyageNumber).toList()));

    /** 区間の列を、到着予定の早い順（同時刻は航海番号の順）に列挙する。 */
    public List<List<Leg>> find(RouteSpecification specification, List<Voyage> voyages, UtcInstant judgedAt) {
        Location origin = specification.origin();
        Location destination = specification.destination();
        List<List<Leg>> candidates = new ArrayList<>();
        for (Voyage first : voyages) {
            for (Leg leg : legsFrom(first, origin, judgedAt, false)) {
                if (leg.discharge().equals(destination)) {
                    candidates.add(List.of(leg));
                } else if (!leg.discharge().equals(origin)) {
                    for (Voyage second : voyages) {
                        if (second.voyageNumber().equals(first.voyageNumber())) {
                            continue;
                        }
                        legsFrom(second, leg.discharge(), leg.arrivalAt(), true).stream()
                                .filter(next -> next.discharge().equals(destination))
                                .forEach(next -> candidates.add(List.of(leg, next)));
                    }
                }
            }
        }
        candidates.sort(BY_ARRIVAL_THEN_VOYAGES);
        return List.copyOf(candidates);
    }

    /**
     * 航海が港を出発する寄港から、後の各寄港までの区間。出発地の区間は判定時刻より後（同時刻は出発済みとみなす）に、
     * 積替えの区間は前の区間の到着予定以後（同時刻を含む）に出発するものだけを取る。
     */
    private static List<Leg> legsFrom(Voyage voyage, Location port, UtcInstant after, boolean inclusive) {
        List<PortCall> calls = voyage.portCalls();
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            PortCall load = calls.get(i);
            if (load.port().equals(port) && departsAfter(load, after, inclusive)) {
                for (int j = i + 1; j < calls.size(); j++) {
                    PortCall discharge = calls.get(j);
                    if (discharge.arrivalAt() != null && !discharge.port().equals(port)) {
                        legs.add(new Leg(
                                voyage.voyageNumber(),
                                load.port(),
                                discharge.port(),
                                load.departureAt(),
                                discharge.arrivalAt(),
                                voyage.adoptedInfoVersion(),
                                voyage.acquiredAt()));
                    }
                }
            }
        }
        return legs;
    }

    private static boolean departsAfter(PortCall load, UtcInstant after, boolean inclusive) {
        if (load.departureAt() == null) {
            return false;
        }
        int order = load.departureAt().instant().compareTo(after.instant());
        return inclusive ? order >= 0 : order > 0;
    }
}
