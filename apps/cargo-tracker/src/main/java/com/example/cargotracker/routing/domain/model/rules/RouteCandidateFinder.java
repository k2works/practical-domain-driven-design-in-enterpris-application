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
 * MVP は確定的な列挙とし、最適化はしない。直行から積替え 2 回（区間 1〜3）まで（Bolt 17 は区間 2 まで、Bolt 37 で区間 3 に広げた）。
 *
 * <p>区間は、航海の寄港のうち、出発予定のある寄港から、それより後の到着予定のある寄港までを取る。判定時刻より後に出発する区間だけを
 * 列挙する。積替えは、出発地・目的地でない港で、前の区間の到着予定以後に同じ港を出発する別の航海につなぐ。列の中で同じ航海と
 * 同じ港（出発地を含む）を 2 回使わない（港を巡って戻る列は業務の経路にならない。Bolt 37 の確認ポイント 2）。接続時間が足りるかは
 * 制約適合判定が決める（足りない候補も除外の理由とともに示すため、ここでは捨てない）。
 */
@DomainRule
public class RouteCandidateFinder {

    /** 区間の数の上限（積替え 2 回。区間 4 以上は業務の経路として比べない。W6 の確認ポイント 5）。 */
    private static final int MAX_LEGS = 3;

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
                extend(List.of(leg), origin, destination, voyages, candidates);
            }
        }
        candidates.sort(BY_ARRIVAL_THEN_VOYAGES);
        return List.copyOf(candidates);
    }

    /**
     * 区間の列が目的地に着いていれば候補に加え、着いていなければ、上限の内で最後の揚地から別の航海に積み替えてのばす。
     * 最後の揚地が、出発地か前の区間の揚地なら（港に戻る列）のばさない。
     */
    private static void extend(
            List<Leg> route, Location origin, Location destination, List<Voyage> voyages, List<List<Leg>> candidates) {
        Leg last = route.getLast();
        if (last.discharge().equals(destination)) {
            candidates.add(route);
            return;
        }
        if (route.size() == MAX_LEGS || visitedBefore(route, origin, last.discharge())) {
            return;
        }
        for (Voyage voyage : voyages) {
            if (usedIn(route, voyage)) {
                continue;
            }
            for (Leg next : legsFrom(voyage, last.discharge(), last.arrivalAt(), true, destination)) {
                List<Leg> longer = new ArrayList<>(route);
                longer.add(next);
                extend(List.copyOf(longer), origin, destination, voyages, candidates);
            }
        }
    }

    /** 港が、出発地か、最後の区間より前の区間の揚地か。 */
    private static boolean visitedBefore(List<Leg> route, Location origin, Location port) {
        return port.equals(origin)
                || route.subList(0, route.size() - 1).stream()
                        .anyMatch(leg -> leg.discharge().equals(port));
    }

    /** 航海を列のどこかで使ったか（続けて使う列と、間を空けて使う列の両方を除く）。 */
    private static boolean usedIn(List<Leg> route, Voyage voyage) {
        return route.stream().anyMatch(leg -> leg.voyageNumber().equals(voyage.voyageNumber()));
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
