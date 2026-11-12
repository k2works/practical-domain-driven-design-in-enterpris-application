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
import java.util.Optional;

/**
 * 最新の到着見込みの規則。予定と採用の実績の列から最新の到着見込みを返す純粋な関数（T-INV-10。Bolt 39g3。【業務の判断】(an)・(ao)）。
 *
 * <p>採用の実績のうち出発・積替・到着を、予定の時点（{@link Schedule#pointOf}。Bolt 39g4 で予定に出した結び方）に結ぶ。結んだ実績のうち予定時刻の
 * 最も後のもの（同じ予定時刻なら発生時刻の遅いもの）を使い、到着ならその発生時刻（早着も実績にする）、出発・積替なら当初の到着予定に正の遅れ
 * だけを足す（早い出発で見込みを早めない。航海の到着予定は出発が早くても変わらない）。結んだ実績がなければ当初の到着予定。確認中・下書き・
 * 保持のみは現在状態に効かないので使わない（T-INV-08 と同じ）。
 *
 * <p>最後の区間の航海の到着予定（DE-12。Bolt 39g4b。【業務の判断】(at)、台帳 D-136）があれば、到着の実績がないときに、実績の遅れで送った見込み（当初の
 * 到着予定以上）と航海の到着予定の遅い方にする。航海の到着予定が早まっても当初の到着予定より早めない。到着の実績があれば実績のまま。
 */
@DomainRule
public class LatestEstimateRule {

    /**
     * 航海の到着予定のない最新の到着見込みを返す（Bolt 39g3 の形。航海の到着予定を空にして {@link #estimate(Schedule, Collection, Optional)} に委ねる）。
     *
     * @param schedule 予定（当初の到着予定は最後の区間の到着予定）
     * @param milestones 追跡記録の実績の列
     * @return 最新の到着見込み
     */
    public UtcInstant estimate(Schedule schedule, Collection<Milestone> milestones) {
        return estimate(schedule, milestones, Optional.empty());
    }

    /**
     * 最新の到着見込みを返す。
     *
     * @param schedule 予定（当初の到着予定は最後の区間の到着予定）
     * @param milestones 追跡記録の実績の列
     * @param voyageArrival 最後の区間の航海の到着予定（なければ空）
     * @return 最新の到着見込み
     */
    @SuppressWarnings("java:S3553") // 航海の到着予定は追跡記録が空で持つ値をそのまま渡す（集約の Optional の値）
    public UtcInstant estimate(
            Schedule schedule, Collection<Milestone> milestones, Optional<UtcInstant> voyageArrival) {
        UtcInstant original = schedule.finalArrival();
        Optional<Matched> latest = milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .flatMap(
                        milestone -> schedule
                                .pointOf(milestone.kind(), milestone.location())
                                .map(point -> new Matched(milestone, point))
                                .stream())
                .max(Comparator.comparing((Matched matched) ->
                                matched.point().scheduledAt().instant())
                        .thenComparing(
                                matched -> matched.milestone().occurredAt().instant()));
        if (latest.isPresent() && latest.get().milestone().kind() == MilestoneKind.ARRIVAL) {
            return latest.get().milestone().occurredAt();
        }
        UtcInstant byMilestones =
                latest.map(matched -> estimateFrom(matched, original)).orElse(original);
        return voyageArrival
                .filter(arrival -> arrival.instant().isAfter(byMilestones.instant()))
                .orElse(byMilestones);
    }

    /** 出発・積替の実績の正の遅れを当初の到着予定に足す（到着の実績は呼び手が先に返す）。 */
    private static UtcInstant estimateFrom(Matched matched, UtcInstant original) {
        Milestone milestone = matched.milestone();
        Duration delay = Duration.between(
                matched.point().scheduledAt().instant(), milestone.occurredAt().instant());
        return delay.isPositive() ? new UtcInstant(original.instant().plus(delay)) : original;
    }

    /** 予定の時点に結んだ実績。 */
    private record Matched(Milestone milestone, SchedulePoint point) {}
}
