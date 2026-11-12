package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.SchedulePoint;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;

/**
 * 最新の到着見込みの規則。予定と採用の実績の列から最新の到着見込みを返す純粋な関数（T-INV-10。Bolt 39g3。【業務の判断】(an)・(ao)）。
 *
 * <p>採用の実績のうち出発・積替・到着を、予定の時点（{@link Schedule#pointOf}。Bolt 39g4 で予定に出した結び方）に結ぶ。結んだ実績のうち予定時刻の
 * 最も後のもの（同じ予定時刻なら発生時刻の遅いもの）を使い、到着ならその発生時刻（早着も実績にする）、出発・積替なら当初の到着予定に正の遅れ
 * だけを足す（早い出発で見込みを早めない。航海の到着予定は出発が早くても変わらない）。結んだ実績がなければ当初の到着予定。確認中・下書き・
 * 保持のみは現在状態に効かないので使わない（T-INV-08 と同じ）。航海の採用値（DE-12）で動かす分は Bolt 39g4b。
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
                .flatMap(
                        milestone -> schedule
                                .pointOf(milestone.kind(), milestone.location())
                                .map(point -> new Matched(milestone, point))
                                .stream())
                .max(Comparator.comparing((Matched matched) ->
                                matched.point().scheduledAt().instant())
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
                matched.point().scheduledAt().instant(), milestone.occurredAt().instant());
        return delay.isPositive() ? new UtcInstant(original.instant().plus(delay)) : original;
    }

    /** 予定の時点に結んだ実績。 */
    private record Matched(Milestone milestone, SchedulePoint point) {}
}
