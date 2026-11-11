package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.ScheduledLeg;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * 最新の到着見込みの規則。予定と採用の実績の列から最新の到着見込みを返す純粋な関数（T-INV-10。Bolt 39g3。【業務の判断】(an)・(ao)）。
 *
 * <p>採用の実績のうち出発・積替・到着を、場所と区間の位置で予定区間の時刻に結ぶ。出発は積地が実績の場所の区間の出発予定、積替は揚地が実績の場所の
 * 区間（最後の区間でない）の到着予定、到着は最後の区間の揚地が実績の場所なら最後の区間の到着予定。集荷・搬入・引渡しは予定に時刻がないので結ばない。
 * 場所が区間にない実績、種類と区間の位置が合わない実績、同じ場所が 2 つ以上の区間に当たる実績は結ばない。結んだ実績のうち予定の順で最も後のもの
 * （同じ予定時刻なら発生時刻の遅いもの）を使い、到着ならその発生時刻（早着も実績にする）、出発・積替なら当初の到着予定に正の遅れだけを足す
 * （早い出発で見込みを早めない。航海の到着予定は出発が早くても変わらない）。結んだ実績がなければ当初の到着予定。確認中・下書き・保持のみは
 * 現在状態に効かないので使わない（T-INV-08 と同じ）。航海の採用値（DE-12）で動かす分は Bolt 39g4。
 */
@DomainRule
public class LatestEstimateRule {

    /**
     * 最新の到着見込みを返す。
     *
     * @param schedule 予定（当初の到着予定は最後の区間の到着予定）
     * @param milestones 追跡記録の実績の列
     * @return 最新の到着見込み
     */
    public UtcInstant estimate(Schedule schedule, Collection<Milestone> milestones) {
        UtcInstant original = schedule.finalArrival();
        return milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .flatMap(milestone ->
                        scheduledAt(schedule, milestone)
                                .map(scheduledAt -> new Matched(milestone, scheduledAt))
                                .stream())
                .max(Comparator.comparing(
                                (Matched matched) -> matched.scheduledAt().instant())
                        .thenComparing(
                                matched -> matched.milestone().occurredAt().instant()))
                .map(matched -> estimateFrom(matched, original))
                .orElse(original);
    }

    private static UtcInstant estimateFrom(Matched matched, UtcInstant original) {
        Milestone milestone = matched.milestone();
        if (milestone.kind() == MilestoneKind.ARRIVAL) {
            return milestone.occurredAt();
        }
        Duration delay = Duration.between(
                matched.scheduledAt().instant(), milestone.occurredAt().instant());
        return delay.isPositive() ? new UtcInstant(original.instant().plus(delay)) : original;
    }

    /** 実績を結ぶ予定区間の時刻（結ばなければ空）。 */
    private static Optional<UtcInstant> scheduledAt(Schedule schedule, Milestone milestone) {
        List<ScheduledLeg> legs = schedule.legs();
        int last = legs.size() - 1;
        Location location = milestone.location();
        return switch (milestone.kind()) {
            case DEPARTURE ->
                onlyLeg(legs, index -> legs.get(index).load().equals(location))
                        .map(index -> legs.get(index).departureAt());
            case TRANSSHIPMENT ->
                onlyLeg(
                                legs,
                                index -> index != last
                                        && legs.get(index).discharge().equals(location))
                        .map(index -> legs.get(index).arrivalAt());
            case ARRIVAL ->
                legs.get(last).discharge().equals(location)
                        ? Optional.of(legs.get(last).arrivalAt())
                        : Optional.empty();
            case PICKUP, RECEIPT_AT_ORIGIN, DELIVERY -> Optional.empty();
        };
    }

    /** 条件に当たる区間がちょうど 1 つならその番号（0 か 2 つ以上なら空。同じ港を 2 度使う予定の守り）。 */
    private static Optional<Integer> onlyLeg(List<ScheduledLeg> legs, Predicate<Integer> matches) {
        List<Integer> matched =
                IntStream.range(0, legs.size()).boxed().filter(matches).toList();
        return matched.size() == 1 ? Optional.of(matched.getFirst()) : Optional.empty();
    }

    /** 予定区間の時刻に結んだ実績。 */
    private record Matched(Milestone milestone, UtcInstant scheduledAt) {}
}
