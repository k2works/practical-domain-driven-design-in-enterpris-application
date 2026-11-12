package com.example.cargotracker.tracking.domain.model.rules;

import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import com.example.cargotracker.tracking.domain.model.entities.Milestone;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * 候補の規則。組の実績の新しさを、公開の順（{@link PublicationOrder}。公開した時刻 → 取得時刻 → 受信記録 ID（文字列の順））で比べる純粋な関数（Bolt 39gb の行 5。
 * 【業務の判断】(aa)。Bolt 39g4b で比べ方を値に出した）。
 *
 * <p>外部版と実績番号は比べない（外部版は順の定めのない文字列で、実績番号は届いた順。届いた順で候補を変えない）。主要実績は削除・上書きしないので、書くときには
 * 比べず、読むときに組から最も新しいものを決める（航海の到着予定は書くときに比べる。使い方の違いは {@link PublicationOrder} に書いた）。公開の順は
 * 共有カーネルに上げずに追跡のドメインに置く（Bolt 39gb の確認ポイント 2、Bolt 39g4b の決定 (5)）。
 */
@DomainRule
public class MilestoneCandidateRule {

    private static final Comparator<Milestone> BY_FRESHNESS = Comparator.comparing(MilestoneCandidateRule::orderOf);

    /** 外部原本から受けた実績の公開の順（公開した時刻は外部の識別、取得時刻と受信記録 ID は出典）。 */
    private static PublicationOrder orderOf(Milestone milestone) {
        return new PublicationOrder(
                milestone.externalOrigin().publishedAt(),
                milestone.source().acquiredAt(),
                milestone.source().reference());
    }

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
