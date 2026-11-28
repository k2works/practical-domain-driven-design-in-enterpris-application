package com.example.cargotracker.externaldata.domain.model.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.externaldata.domain.model.rules.DiscrepancyRule.Discrepancy;
import com.example.cargotracker.externaldata.domain.model.valueobjects.DiscrepancyReason;
import com.example.cargotracker.externaldata.domain.model.valueobjects.ExternalIdAdoptedValue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 差異の規則（US-14 AC5、ED-INV-03。Bolt 39h の状態の組み合わせの行 1。開始準備の決定 (3)・(4)、【業務の判断】(ay)〜(ba)）。今受け取った原本の
 * 対象のキーと取得時刻と、この外部 ID の採用値の一覧から、差異の理由と比べた外部版を決める。取得時刻は G1 &lt; G2 &lt; G3、追跡番号は CTA・CTB、
 * 航海の対象のキーは航海番号 V100。期待値は決定の文から導いた（Bolt 39g4 の Try）。
 */
class DiscrepancyRuleTest {

    private static final Instant G1 = Instant.parse("2026-11-03T08:00:00Z");
    private static final Instant G2 = Instant.parse("2026-11-03T08:30:00Z");
    private static final Instant G3 = Instant.parse("2026-11-03T09:00:00Z");
    /** 公開した時刻の基点（取込の時刻）。 */
    private static final Instant PUBLISHED = Instant.parse("2026-11-03T10:00:00Z");

    private static final String CTA = "CTABCDEFGH2345";
    private static final String CTB = "CTABCDEFGH2346";
    private static final String VOYAGE = "V100";

    private static ExternalIdAdoptedValue adopted(
            String version, String targetKey, Instant acquiredAt, Instant publishedAt) {
        return new ExternalIdAdoptedValue(version, targetKey, acquiredAt, publishedAt, UUID.randomUUID());
    }

    private static ExternalIdAdoptedValue adopted(String version, String targetKey, Instant acquiredAt) {
        return adopted(version, targetKey, acquiredAt, PUBLISHED);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                Arguments.of("(a) 一覧が空", CTA, G2, List.of(), null, null),
                Arguments.of("(b) G1・CTA に G2・CTA", CTA, G2, List.of(adopted("1", CTA, G1)), null, null),
                Arguments.of("(c) G2・CTA に同じ取得時刻の G2・CTA", CTA, G2, List.of(adopted("1", CTA, G2)), null, null),
                Arguments.of(
                        "(d) G2・CTA に G1・CTA",
                        CTA,
                        G1,
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of(
                        "(e) G1・CTA に G2・CTB",
                        CTB,
                        G2,
                        List.of(adopted("1", CTA, G1)),
                        DiscrepancyReason.TARGET_KEY_DIFFERS,
                        "1"),
                Arguments.of(
                        "(f) G2・CTA に G1・CTB は逆転を先に見る",
                        CTB,
                        G1,
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of(
                        "(g) 航海の G2 に G1",
                        VOYAGE,
                        G1,
                        List.of(adopted("2", VOYAGE, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                Arguments.of("(h1) 外部版 9・G1 に外部版 10・G2", CTA, G2, List.of(adopted("9", CTA, G1)), null, null),
                Arguments.of("(h2) 外部版 2・G1 に外部版 1・G2", CTA, G2, List.of(adopted("2", CTA, G1)), null, null),
                // 外部版の文字列の順で最後（"9"）でなく、取得時刻の最も遅い "10" と比べる（変異 M4 を規則の層で縛る。39g4c の Try）
                Arguments.of(
                        "(h3) 外部版 10・G3 と外部版 9・G1 に G2",
                        CTA,
                        G2,
                        List.of(adopted("10", CTA, G3), adopted("9", CTA, G1)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "10"),
                Arguments.of(
                        "(i) G2 に 1 マイクロ秒前",
                        CTA,
                        G2.minusNanos(1_000),
                        List.of(adopted("2", CTA, G2)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "2"),
                // 公開した時刻の順で最も新しい V1 でなく、最後に取得した V3 と比べる（決定 (3)。変異 M10 を規則の層で縛る）
                Arguments.of(
                        "(j) V1・G1（後に公開）と V3・G3（先に公開）に G2・CTA",
                        CTA,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("3", CTA, G3, PUBLISHED)),
                        DiscrepancyReason.VERSION_REVERSED,
                        "3"),
                // 矛盾の相手は公開した時刻の順で最も新しい V1（CTA）。取得時刻の順の V2（CTB）ではない（D-105 の順）
                Arguments.of(
                        "(k) V1・G1・CTA（後に公開）と V2・G1・CTB（先に公開）に G2・CTA",
                        CTA,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("2", CTB, G1, PUBLISHED)),
                        null,
                        null),
                Arguments.of(
                        "(k2) 同じ組に G2・CTB",
                        CTB,
                        G2,
                        List.of(adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600)), adopted("2", CTB, G1, PUBLISHED)),
                        DiscrepancyReason.TARGET_KEY_DIFFERS,
                        "1"));
    }

    @ParameterizedTest(name = "行1 {0} → {4} {5}")
    @MethodSource("rows")
    void 行1_差異の理由と比べた外部版を決める(
            String name,
            String targetKey,
            Instant acquiredAt,
            List<ExternalIdAdoptedValue> adopted,
            DiscrepancyReason reason,
            String comparedVersion) {
        Optional<Discrepancy> discrepancy = DiscrepancyRule.discrepancyOf(targetKey, acquiredAt, adopted);

        if (reason == null) {
            assertThat(discrepancy).isEmpty();
        } else {
            assertThat(discrepancy).contains(new Discrepancy(reason, comparedVersion));
        }
    }

    /**
     * Bolt 39h3 の行 1・2（版の逆転の判定・今使っている採用値・比べる相手の取り出し。開始準備の決定 (2)）。取り出しは振る舞いを変えないので、上の
     * 既存の組が緑のままであることと合わせて縛る。警告の判定と判断の画面の領域が、差異の規則と同じ定義を共有する。
     */
    @Nested
    class Bolt39h3_判定と比べる相手の取り出し {

        private static final ExternalIdAdoptedValue v1 = adopted("1", VOYAGE, G1, PUBLISHED);
        private static final ExternalIdAdoptedValue v2 = adopted("2", VOYAGE, G2, PUBLISHED.plusSeconds(60));

        @ParameterizedTest(name = "行1 {0}")
        @MethodSource("versionReversedRows")
        void 行1_版の逆転は最後に取得した採用値より厳密に前のときだけ真(
                String name, Instant acquiredAt, List<ExternalIdAdoptedValue> adopted, boolean expected) {
            assertThat(DiscrepancyRule.isVersionReversed(acquiredAt, adopted)).isEqualTo(expected);
        }

        static Stream<Arguments> versionReversedRows() {
            return Stream.of(
                    Arguments.of("(a) 採用値なし", G1, List.of(), false),
                    Arguments.of("(b) 厳密に前", G1, List.of(v2), true),
                    Arguments.of("(c) 同じ時刻は逆転でない（D-142）", G2, List.of(v2), false),
                    Arguments.of("(d) 後", G3, List.of(v2), false),
                    // 最後に取得した V2（G2）と比べる。最初の V1（G1）と比べると偽になる
                    Arguments.of("(e) 複数の採用値は最後に取得したものと比べる", G2.minusSeconds(1), List.of(v2, v1), true),
                    // 外部版を問わない（D-142 の定義のまま）
                    Arguments.of("(f) 前で最後に取得した採用値が同じ外部版", G1, List.of(adopted("2", VOYAGE, G2)), true));
        }

        @Test
        void 行2_a_空の一覧に今使っている採用値はない() {
            assertThat(DiscrepancyRule.inUseOf(List.of())).isEmpty();
        }

        @Test
        void 行2_b_今使っている採用値は公開した時刻が最も新しいもので取得時刻の新しいものではない() {
            ExternalIdAdoptedValue publishedLater = adopted("1", VOYAGE, G1, PUBLISHED.plusSeconds(3600));
            ExternalIdAdoptedValue acquiredLater = adopted("3", VOYAGE, G3, PUBLISHED);

            assertThat(DiscrepancyRule.inUseOf(List.of(acquiredLater, publishedLater)))
                    .contains(publishedLater);
        }

        @Test
        void 行2_c_公開した時刻が同じなら取得時刻の新しいもの() {
            ExternalIdAdoptedValue earlier = adopted("1", VOYAGE, G1, PUBLISHED);
            ExternalIdAdoptedValue later = adopted("2", VOYAGE, G2, PUBLISHED);

            assertThat(DiscrepancyRule.inUseOf(List.of(later, earlier))).contains(later);
            assertThat(DiscrepancyRule.inUseOf(List.of(earlier, later))).contains(later);
        }

        @Test
        void 行2_d_さらに同じなら受信記録IDの文字列の最後() {
            ExternalIdAdoptedValue low = new ExternalIdAdoptedValue(
                    "1", VOYAGE, G1, PUBLISHED, UUID.fromString("00000000-0000-0000-0000-000000000001"));
            ExternalIdAdoptedValue high = new ExternalIdAdoptedValue(
                    "2", VOYAGE, G1, PUBLISHED, UUID.fromString("00000000-0000-0000-0000-000000000002"));

            assertThat(DiscrepancyRule.inUseOf(List.of(high, low))).contains(high);
            assertThat(DiscrepancyRule.inUseOf(List.of(low, high))).contains(high);
        }

        @Test
        void 行2_a_空の一覧に比べる相手はない() {
            assertThat(DiscrepancyRule.partnerOf(G1, List.of())).isEmpty();
        }

        @Test
        void 行2_e_逆転のとき比べる相手は最後に取得した採用値で差異の注記の相手と同じ() {
            // V1 を公開した時刻が最も新しい（今使っている採用値）が、最後に取得したのは V2
            ExternalIdAdoptedValue inUse = adopted("1", VOYAGE, G1, PUBLISHED.plusSeconds(3600));
            ExternalIdAdoptedValue lastAcquired = adopted("2", VOYAGE, G2, PUBLISHED);
            List<ExternalIdAdoptedValue> adopted = List.of(inUse, lastAcquired);

            assertThat(DiscrepancyRule.partnerOf(G1, adopted)).contains(lastAcquired);
            assertThat(DiscrepancyRule.discrepancyOf(VOYAGE, G1, adopted))
                    .contains(new Discrepancy(DiscrepancyReason.VERSION_REVERSED, "2"));
        }

        @Test
        void 行2_f_逆転でないとき比べる相手は今使っている採用値で矛盾の相手と同じ() {
            ExternalIdAdoptedValue inUse = adopted("1", CTA, G1, PUBLISHED.plusSeconds(3600));
            ExternalIdAdoptedValue other = adopted("2", CTB, G1, PUBLISHED);
            List<ExternalIdAdoptedValue> adopted = List.of(other, inUse);

            assertThat(DiscrepancyRule.partnerOf(G2, adopted))
                    .contains(inUse)
                    .isEqualTo(DiscrepancyRule.inUseOf(adopted));
            assertThat(DiscrepancyRule.discrepancyOf(CTB, G2, adopted))
                    .contains(new Discrepancy(DiscrepancyReason.TARGET_KEY_DIFFERS, "1"));
        }

        @Test
        void 行2_g_V2の取込の後にV1を採用した後で逆転のとき比べる相手は最後に取得したV2で今使っているV1とは別() {
            ExternalIdAdoptedValue v1Again = adopted("1", VOYAGE, G1.plusSeconds(60), PUBLISHED.plusSeconds(7200));
            List<ExternalIdAdoptedValue> adopted = List.of(v1, v2, v1Again);

            assertThat(DiscrepancyRule.inUseOf(adopted)).contains(v1Again);
            assertThat(DiscrepancyRule.partnerOf(G1.plusSeconds(120), adopted)).contains(v2);
        }
    }
}
