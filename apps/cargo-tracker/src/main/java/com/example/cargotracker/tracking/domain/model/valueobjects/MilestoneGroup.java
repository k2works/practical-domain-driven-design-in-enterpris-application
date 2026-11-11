package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.rules.MilestoneCandidateRule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 組。同じ追跡番号・同じ情報源 ID・同じ外部 ID の、外部原本から受けた下書きと採用と確認中の実績（Bolt 39gb。【業務の判断】(aa)）。保持のみと手入力の実績は
 * 入らない。確認中は判断済みとして採用済みと同じに扱う（Bolt 39gc。【業務の判断】(ai)。抜けると同じ組の古い下書きが候補に戻る）。
 * 状態を持たず、追跡記録が読むたびに実績から作る（書くときに比べないので、届いた順によらず同じ候補になる）。
 *
 * @param dataSourceId 情報源 ID
 * @param externalId 外部 ID
 * @param members 組の実績（実績番号の順）
 */
@ValueObject
public record MilestoneGroup(UUID dataSourceId, String externalId, List<Milestone> members) {

    private static final MilestoneCandidateRule RULE = new MilestoneCandidateRule();

    public MilestoneGroup {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(externalId, "externalId");
        members = List.copyOf(members);
        for (Milestone member : members) {
            ExternalOrigin origin = member.externalOrigin();
            boolean inGroup = origin != null
                    && origin.dataSourceId().equals(dataSourceId)
                    && origin.externalId().equals(externalId)
                    && isGroupState(member.state());
            if (!inGroup) {
                throw new IllegalArgumentException("組に入らない実績です: 実績 " + member.milestoneNo());
            }
        }
    }

    /** 組に入る実績の状態か（下書きと、判断済みの採用・確認中）。 */
    public static boolean isGroupState(MilestoneState state) {
        return state == MilestoneState.DRAFT || isDecided(state);
    }

    private static boolean isDecided(MilestoneState state) {
        return state == MilestoneState.ADOPTED || state == MilestoneState.UNDER_REVIEW;
    }

    /** 候補。組で最も新しい実績が下書きなら、それを返す（最も新しい実績が判断済みなら候補はない）。 */
    public Optional<Milestone> candidate() {
        return RULE.newest(members).filter(newest -> newest.state() == MilestoneState.DRAFT);
    }

    /** 組の判断済み（採用・確認中）の実績のうち最も新しいもの（Bolt 39gc で確認中に広げた）。 */
    public Optional<Milestone> adopted() {
        return RULE.newest(
                members.stream().filter(member -> isDecided(member.state())).toList());
    }

    /** 置き換えられた下書きか（組の下書きで、候補でない）。 */
    public boolean isSuperseded(Milestone milestone) {
        return milestone.state() == MilestoneState.DRAFT
                && members.contains(milestone)
                && candidate().filter(milestone::equals).isEmpty();
    }
}
