package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import com.example.cargotracker.tracking.domain.model.valueobjects.Schedule;
import com.example.cargotracker.tracking.domain.model.valueobjects.SchedulePoint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 次の予定の実績の規則。予定と採用の実績の列から次の予定の時点を返す純粋な関数（FRS-02。Bolt 39g4。【業務の判断】(aq)）。
 *
 * <p>採用の実績を予定の時点（{@link Schedule#pointOf}。最新の到着見込みの規則と同じ結び方）に結び、結んだ時点のうち予定の時点の列で最も後のものの次を
 * 返す。結んだ時点がなければ最初の時点（区間 1 の出発）、最後の時点（最後の区間の到着）を結んだら空。時点の列の位置で次を決めるので、積替予定と次の
 * 区間の出発予定が同じ時刻でも積替の次は次の区間の出発。登録の順・発生時刻の順は見ない。確認中・下書き・保持のみは使わない（T-INV-08 と同じ）。
 * 完了後かどうかは見ない（追跡記録が完了後なら使わない）。
 */
@DomainRule
public class NextExpectedMilestoneRule {

    /**
     * 次の予定の時点を返す。
     *
     * @param schedule 予定
     * @param milestones 追跡記録の実績の列
     * @return 次の予定の時点（最後の区間の到着を結んだら空）
     */
    public Optional<SchedulePoint> next(Schedule schedule, Collection<Milestone> milestones) {
        List<SchedulePoint> points = schedule.points();
        int latest = milestones.stream()
                .filter(milestone -> milestone.state() == MilestoneState.ADOPTED)
                .flatMap(milestone -> schedule.pointOf(milestone.kind(), milestone.location()).stream())
                .mapToInt(points::indexOf)
                .max()
                .orElse(-1);
        int next = latest + 1;
        return next < points.size() ? Optional.of(points.get(next)) : Optional.empty();
    }
}
