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
            for (Leg leg : legsFrom(first, origin, judgedAt, false, destination)) {
                if (leg.discharge().equals(destination)) {
                    candidates.add(List.of(leg));
                } else if (!leg.discharge().equals(origin)) {
                    candidates.addAll(transshipments(leg, first, voyages, destination));
                }
            }
        }
        candidates.sort(BY_ARRIVAL_THEN_VOYAGES);
        return List.copyOf(candidates);
    }

    /** 1 区間目の揚地で、別の航海に積み替えて目的地へ着く区間 2 の列。 */
    private static List<List<Leg>> transshipments(
            Leg first, Voyage firstVoyage, List<Voyage> voyages, Location destination) {
        return voyages.stream()
                .filter(second -> !second.voyageNumber().equals(firstVoyage.voyageNumber()))
                .flatMap(second -> legsFrom(second, first.discharge(), first.arrivalAt(), true, null).stream())
                .filter(next -> next.discharge().equals(destination))
                .map(next -> List.of(first, next))
                .toList();
    }

    /**
     * 航海が港を出発する寄港から、後の各寄港までの区間。出発地の区間は判定時刻より後（同時刻は出発済みとみなす）に、
     * 積替えの区間は前の区間の到着予定以後（同時刻を含む）に出発するものだけを取る。
     */
    private static List<Leg> legsFrom(
            Voyage voyage, Location port, UtcInstant after, boolean inclusive, Location passing) {
        List<PortCall> calls = voyage.portCalls();
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            PortCall load = calls.get(i);
            if (load.port().equals(port) && departsAfter(load, after, inclusive)) {
                legs.addAll(legsFromCall(voyage, i, passing));
            }
        }
        return legs;
    }

    /**
     * 寄港 {@code from} から後の各寄港までの区間。積地と同じ港へは運ばない。途中で {@code passing}（目的地）に寄ったら、
     * その先の港へは運ばない（目的地を通り過ぎてから積み替えて戻る候補は、直行に劣り比較を惑わせる。Bolt 17 レビュー D-60）。
     */
    private static List<Leg> legsFromCall(Voyage voyage, int from, Location passing) {
        List<PortCall> calls = voyage.portCalls();
        PortCall load = calls.get(from);
        List<Leg> legs = new ArrayList<>();
        for (int j = from + 1; j < calls.size(); j++) {
            PortCall discharge = calls.get(j);
            if (discharge.arrivalAt() != null && !discharge.port().equals(load.port())) {
                legs.add(new Leg(
                        voyage.voyageNumber(),
                        load.port(),
                        discharge.port(),
                        load.departureAt(),
                        discharge.arrivalAt(),
                        voyage.adoptedInfoVersion(),
                        voyage.acquiredAt()));
            }
            if (discharge.port().equals(passing)) {
                break;
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
