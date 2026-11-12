package com.example.cargotracker.externaldata.domain.model.rules;

import com.example.cargotracker.externaldata.domain.model.valueobjects.DiscrepancyReason;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import com.example.cargotracker.shared.annotation.ddd.DomainRule;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 差異の規則。今受け取った原本の対象のキーと取得時刻を、この外部 ID の採用値の一覧と比べ、自動で採用せず差異にする理由を決める（US-14 AC5、
 * ED-INV-03。Bolt 39h の開始準備の決定 (3)・(4)、【業務の判断】(ay)〜(ba)）。比べる相手は 2 つで、版の逆転は「この外部 ID の最後に取得した採用値」
 * （取得時刻の最も遅いもの。同じなら外部版の文字列の順の最後）、矛盾は「この外部 ID の採用値」（公開した時刻 → 取得時刻 → 受信記録 ID（文字列）の
 * 順で最も新しいもの。D-105 の順）。逆転を先に見る。外部版の文字列どうしの順は比べない（外部版は順の定めのない文字列。D-105）。
 */
@DomainRule
public final class DiscrepancyRule {

    /** 版の逆転の相手の順（取得時刻、同じなら外部版の文字列。最後が相手）。 */
    private static final Comparator<ExternalIdAdoptedValue> ACQUIRED_ORDER = Comparator.comparing(
                    ExternalIdAdoptedValue::acquiredAt)
            .thenComparing(ExternalIdAdoptedValue::externalVersion);

    /** 矛盾の相手の順（D-105。公開した時刻 → 取得時刻 → 受信記録 ID の文字列の表現。最後が相手）。 */
    private static final Comparator<ExternalIdAdoptedValue> PUBLISHED_ORDER = Comparator.comparing(
                    ExternalIdAdoptedValue::publishedAt)
            .thenComparing(ExternalIdAdoptedValue::acquiredAt)
            .thenComparing(value -> value.receivedRecordId().toString());

    private DiscrepancyRule() {}

    /**
     * 差異。理由と、比べた相手の外部版（注記と DE-31 に書く）。
     *
     * @param reason 差異の理由
     * @param comparedExternalVersion 比べた相手の外部版
     */
    public record Discrepancy(DiscrepancyReason reason, String comparedExternalVersion) {

        public Discrepancy {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(comparedExternalVersion, "comparedExternalVersion");
        }
    }

    /**
     * 差異を決める。
     *
     * @param targetKey 今受け取った原本の対象のキー（航海は航海番号、主要実績は追跡番号）
     * @param acquiredAt 今受け取った原本の取得時刻
     * @param adopted この外部 ID の採用値の一覧（並びは問わない）
     * @return 差異（差異でなければ空）
     */
    public static Optional<Discrepancy> discrepancyOf(
            String targetKey, Instant acquiredAt, List<ExternalIdAdoptedValue> adopted) {
        Objects.requireNonNull(targetKey, "targetKey");
        Optional<ExternalIdAdoptedValue> partner = partnerOf(acquiredAt, adopted);
        if (isVersionReversed(acquiredAt, adopted)) {
            return partner.map(last -> new Discrepancy(DiscrepancyReason.VERSION_REVERSED, last.externalVersion()));
        }
        return partner.filter(current -> !current.targetKey().equals(targetKey))
                .map(current -> new Discrepancy(DiscrepancyReason.TARGET_KEY_DIFFERS, current.externalVersion()));
    }

    /**
     * 版の逆転か。今の取得時刻が、この外部 ID の最後に取得した採用値の取得時刻より厳密に前（D-142。同じ時刻は逆転でない。外部版は問わない）。
     * 警告の判定と画面の領域が、差異の判定と同じ定義を共有する（Bolt 39h3 の開始準備の決定 (2)）。
     *
     * @param acquiredAt 今の取得時刻
     * @param adopted この外部 ID の採用値の一覧（並びは問わない。空なら偽）
     * @return 版の逆転か
     */
    public static boolean isVersionReversed(Instant acquiredAt, List<ExternalIdAdoptedValue> adopted) {
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        return lastAcquiredOf(adopted)
                .filter(last -> acquiredAt.isBefore(last.acquiredAt()))
                .isPresent();
    }

    /**
     * 今使っている採用値（矛盾の相手の選び方と同じ順。公開した時刻 → 取得時刻 → 受信記録 ID の文字列の最後。D-105）。
     *
     * @param adopted この外部 ID の採用値の一覧（並びは問わない）
     * @return 今使っている採用値（一覧が空なら空）
     */
    public static Optional<ExternalIdAdoptedValue> inUseOf(List<ExternalIdAdoptedValue> adopted) {
        return adopted.stream().max(PUBLISHED_ORDER);
    }

    /**
     * 比べる相手。版の逆転ならこの外部 ID の最後に取得した採用値、そうでなければ今使っている採用値。差異の判定と判断の画面の「この外部 ID の
     * 採用値」が同じ相手を指す。
     *
     * @param acquiredAt 今の取得時刻
     * @param adopted この外部 ID の採用値の一覧（並びは問わない）
     * @return 比べる相手（一覧が空なら空）
     */
    public static Optional<ExternalIdAdoptedValue> partnerOf(Instant acquiredAt, List<ExternalIdAdoptedValue> adopted) {
        return isVersionReversed(acquiredAt, adopted) ? lastAcquiredOf(adopted) : inUseOf(adopted);
    }

    private static Optional<ExternalIdAdoptedValue> lastAcquiredOf(List<ExternalIdAdoptedValue> adopted) {
        return adopted.stream().max(ACQUIRED_ORDER);
    }
}
