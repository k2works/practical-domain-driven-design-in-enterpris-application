package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.rules.MilestoneCandidateRule;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 組。同じ追跡番号・同じ情報源 ID・同じ外部 ID の、外部原本から受けた下書きと採用の実績（Bolt 39gb。【業務の判断】(aa)）。保持のみと手入力の実績は入らない。
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
                    && (member.state() == MilestoneState.DRAFT || member.state() == MilestoneState.ADOPTED);
            if (!inGroup) {
                throw new IllegalArgumentException("組に入らない実績です: 実績 " + member.milestoneNo());
            }
        }
    }

    /** 候補。組で最も新しい実績が下書きなら、それを返す（最も新しい実績が採用済みなら候補はない）。 */
    public Optional<Milestone> candidate() {
        return RULE.newest(members).filter(newest -> newest.state() == MilestoneState.DRAFT);
    }

    /** 組の採用済みの実績のうち最も新しいもの。 */
    public Optional<Milestone> adopted() {
        return RULE.newest(members.stream()
                .filter(member -> member.state() == MilestoneState.ADOPTED)
                .toList());
    }

    /** 置き換えられた下書きか（組の下書きで、候補でない）。 */
    public boolean isSuperseded(Milestone milestone) {
        return milestone.state() == MilestoneState.DRAFT
                && members.contains(milestone)
                && candidate().filter(milestone::equals).isEmpty();
    }
}
