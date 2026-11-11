package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * 候補の規則。組の実績の新しさを、公開した時刻 → 取得時刻 → 受信記録 ID（文字列の順）で比べる純粋な関数（Bolt 39gb の行 5。【業務の判断】(aa)）。
 *
 * <p>外部版と実績番号は比べない（外部版は順の定めのない文字列で、実績番号は届いた順。届いた順で候補を変えない）。経路設計の航海の順と同じ並びだが、
 * 主要実績は削除・上書きしないので、共有カーネルに上げずに追跡のドメインに置く（Bolt 39gb の確認ポイント 2）。
 */
@DomainRule
public class MilestoneCandidateRule {

    private static final Comparator<Milestone> BY_FRESHNESS = Comparator.comparing((Milestone milestone) ->
                    milestone.externalOrigin().publishedAt().instant())
            .thenComparing(milestone -> milestone.source().acquiredAt().instant())
            .thenComparing(milestone -> milestone.source().reference());

    /**
     * 外部原本から受けた実績の集まりのうち、最も新しい実績を返す。
     *
     * @param milestones 外部の識別を持つ実績
     * @return 最も新しい実績（集まりが空なら空）
     */
    public Optional<Milestone> newest(Collection<Milestone> milestones) {
        return milestones.stream().max(BY_FRESHNESS);
    }
}
