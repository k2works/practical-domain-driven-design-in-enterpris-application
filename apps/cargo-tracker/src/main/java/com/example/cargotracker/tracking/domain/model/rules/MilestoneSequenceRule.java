package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneKind;
import com.example.cargotracker.tracking.domain.model.valueobjects.MilestoneState;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * 時系列の規則。新しい実績が採用済みの実績と時系列に反するかを判定する純粋な関数（T-INV-03。Bolt 39gc の行 1・2。【業務の判断】(ag)）。
 *
 * <p>実績の種類に進みの段（集荷 1、搬入 2、出発と積替 3、到着 4、引渡し 5）を置き、新しい実績と採用済みの実績の 1 つでも、段の向きと発生時刻の
 * 向きが逆なら時系列に反する。同じ時刻と同じ段は反しない（出発と積替は積替港で繰り返すので同じ段）。比べるのは採用済みの実績だけで、出典をまたぐ。
 * 下書き・保持のみ・確認中は現在状態に効かないので比べない。段は判定のための並びで種類の意味ではないので、{@link MilestoneKind} に持たせない。
 */
@DomainRule
public class MilestoneSequenceRule {

    private static final Map<MilestoneKind, Integer> STAGES = new EnumMap<>(Map.of(
            MilestoneKind.PICKUP, 1,
            MilestoneKind.RECEIPT_AT_ORIGIN, 2,
            MilestoneKind.DEPARTURE, 3,
            MilestoneKind.TRANSSHIPMENT, 3,
            MilestoneKind.ARRIVAL, 4,
            MilestoneKind.DELIVERY, 5));

    /**
     * 新しい実績が、列の採用済みの実績の 1 つと時系列に反するか。
     *
     * @param milestone 新しい実績（登録する実績か、採用する候補）
     * @param milestones 追跡記録の実績の列
     * @return 時系列に反するなら真
     */
    public boolean isOutOfSequence(Milestone milestone, Collection<Milestone> milestones) {
        return milestones.stream()
                .filter(other -> other.state() == MilestoneState.ADOPTED)
                .anyMatch(adopted -> contradicts(milestone, adopted));
    }

    private static boolean contradicts(Milestone milestone, Milestone adopted) {
        int stage = Integer.compare(stage(milestone.kind()), stage(adopted.kind()));
        int time =
                milestone.occurredAt().instant().compareTo(adopted.occurredAt().instant());
        return Integer.signum(stage) * Integer.signum(time) < 0;
    }

    private static int stage(MilestoneKind kind) {
        Integer stage = STAGES.get(kind);
        if (stage == null) {
            throw new IllegalStateException("実績の種類に進みの段がありません: " + kind);
        }
        return stage;
    }
}
