package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 航海の到着予定の規則。予定と航海の寄港の並びから最後の揚地への到着を結ぶ純粋な関数（Bolt 39g4b。【業務の判断】(au)、台帳 D-137）。
 *
 * <p>寄港の並び（運航の順）で、最後の区間の積地の港で出発のある寄港がちょうど 1 つあれば、その後ろで最後の区間の揚地の港で到着のある最初の寄港の到着を返す
 * （到着のない同じ港の寄港は飛ばす）。積地の寄港がない・2 つ以上（周回の航路。時刻なしではどの周回か決められない）、積地の後に揚地がないときは空。積地・揚地の
 * 読み方は経路設計の {@code Voyage.callPairs} と同じで、積地 1 つの後の揚地が 2 つのときは最初を使う（貨物は最初の揚地で降りる予定）ところが違う。航海番号が
 * 最後の区間の航海であることは呼び手（追跡記録）が確かめる。
 */
@DomainRule
public class VoyageArrivalRule {

    /**
     * 最後の揚地への到着予定を結ぶ。
     *
     * @param schedule 予定（最後の区間の積地と揚地を使う）
     * @param portCalls 最後の区間の航海の寄港の並び（運航の順）
     * @return 到着予定（結べなければ空）
     */
    public Optional<UtcInstant> bind(Schedule schedule, List<VoyagePortCall> portCalls) {
        ScheduledLeg last = schedule.legs().getLast();
        List<Integer> loads = IntStream.range(0, portCalls.size())
                .filter(index -> isLoading(portCalls.get(index), last.load()))
                .boxed()
                .toList();
        if (loads.size() != 1) {
            return Optional.empty();
        }
        return portCalls.subList(loads.getFirst() + 1, portCalls.size()).stream()
                .filter(call -> call.port().equals(last.discharge()))
                .flatMap(call -> call.arrivalAt().stream())
                .findFirst();
    }

    private static boolean isLoading(VoyagePortCall call, Location load) {
        return call.port().equals(load) && call.departureAt().isPresent();
    }
}
