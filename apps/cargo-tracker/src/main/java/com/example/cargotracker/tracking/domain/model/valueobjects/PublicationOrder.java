package com.example.cargotracker.tracking.domain.model.valueobjects;

import com.example.cargotracker.shared.annotation.ddd.ValueObject;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.util.Comparator;
import java.util.Objects;

/**
 * 公開の順。外部データの採用値の新しさを、公開した時刻 → 取得時刻 → 受信記録 ID（文字列の順）で比べる値（D-105。Bolt 39g4b）。外部版と情報版は
 * 順の定めのない文字列なので比べない。
 *
 * <p>追跡の中で使い方が 2 つある。主要実績の候補（{@code MilestoneCandidateRule}）は、削除・上書きしない実績の組から読むときに最も新しいものを決める。
 * 航海の到着予定（{@link VoyageArrival}）は上書きで、書くときに保存した順より厳密に新しい知らせだけを受ける（経路設計の航海と同じ性質）。経路設計の
 * 航海も同じ並びを持つ（写しが 2 か所。3 つ目のコンテキストが要るときに共有カーネルへ上げるかを諮る。Bolt 39g4b の決定 (5)）。
 *
 * @param publishedAt 外部データが採用値を公開した時刻
 * @param acquiredAt 情報の取得時刻（出典の取得時刻）
 * @param sourceReference 出典の参照（受信記録 ID）
 */
@ValueObject
public record PublicationOrder(UtcInstant publishedAt, UtcInstant acquiredAt, String sourceReference)
        implements Comparable<PublicationOrder> {

    private static final Comparator<PublicationOrder> ORDER = Comparator.comparing(
                    (PublicationOrder order) -> order.publishedAt().instant())
            .thenComparing(order -> order.acquiredAt().instant())
            .thenComparing(PublicationOrder::sourceReference);

    public PublicationOrder {
        Objects.requireNonNull(publishedAt, "publishedAt");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(sourceReference, "sourceReference");
    }

    @Override
    public int compareTo(PublicationOrder other) {
        return ORDER.compare(this, other);
    }

    /** この順が {@code other} より厳密に新しいか（同じ順は新しくない。再配信）。 */
    public boolean isNewerThan(PublicationOrder other) {
        return compareTo(other) > 0;
    }
}
