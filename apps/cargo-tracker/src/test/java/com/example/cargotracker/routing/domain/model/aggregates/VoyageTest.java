package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.valueobjects.PortCall;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageAdoption;
import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 航海の集約（Bolt 39e の行 1〜8・46。DE-12 の適用の順は【業務の判断】(r)）。 */
class VoyageTest {

    private static final Location TOKYO = new Location("JPTYO");
    private static final Location SINGAPORE = new Location("SGSIN");
    private static final Location ROTTERDAM = new Location("NLRTM");
    private static final Instant T0 = Instant.parse("2026-11-09T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(3600);
    private static final Instant T2 = T0.plusSeconds(7200);
    private static final String LOW_ID = "00000000-0000-0000-0000-000000000001";
    private static final String HIGH_ID = "ffffffff-0000-0000-0000-000000000001";

    private static final List<PortCall> P1 = calls("2026-12-01T00:00:00Z", "2026-12-20T00:00:00Z");
    private static final List<PortCall> P2 = calls("2026-12-02T00:00:00Z", "2026-12-21T00:00:00Z");
    private static final List<PortCall> THREE_CALLS = List.of(
            new PortCall(TOKYO, null, at("2026-12-01T00:00:00Z")),
            new PortCall(SINGAPORE, at("2026-12-10T00:00:00Z"), at("2026-12-10T12:00:00Z")),
            new PortCall(ROTTERDAM, at("2026-12-20T00:00:00Z"), null));

    private static List<PortCall> calls(String departure, String arrival) {
        return List.of(new PortCall(TOKYO, null, at(departure)), new PortCall(ROTTERDAM, at(arrival), null));
    }

    private static UtcInstant at(String text) {
        return new UtcInstant(Instant.parse(text));
    }

    private static VoyageAdoption adoption(
            List<PortCall> calls,
            String infoVersion,
            SourceKind kind,
            String reference,
            Instant acquired,
            Instant published) {
        return new VoyageAdoption(
                "V2611E",
                calls,
                infoVersion,
                new Source(kind, reference, new UtcInstant(acquired)),
                new UtcInstant(published));
    }

    private static VoyageAdoption adoption(List<PortCall> calls, String infoVersion, Instant published) {
        return adoption(calls, infoVersion, SourceKind.EXTERNAL_RECORD, LOW_ID, T0, published);
    }

    private static void assertHolds(Voyage voyage, VoyageAdoption adoption) {
        assertThat(voyage.portCalls()).isEqualTo(adoption.portCalls());
        assertThat(voyage.adoptedInfoVersion()).isEqualTo(adoption.infoVersion());
        assertThat(voyage.acquiredAt()).isEqualTo(adoption.source().acquiredAt());
        assertThat(voyage.source()).isEqualTo(adoption.source());
        assertThat(voyage.publishedAt()).contains(adoption.publishedAt());
    }

    /** 届いた順に適用して残った航海（最初の 1 つで作り、残りを適用する）。 */
    private static Voyage applyInOrder(VoyageAdoption... adoptions) {
        Voyage voyage = Voyage.adoptedFrom(adoptions[0]);
        Arrays.stream(adoptions).skip(1).forEach(voyage::apply);
        return voyage;
    }

    @Nested
    class Bolt39e_採用した値の適用 {

        @Test
        void 行1_初めてのDE12から寄港と情報版と取得時刻と出典と公開した時刻を持つ版0の航海を作る() {
            String receivedRecordId = UUID.randomUUID().toString();
            VoyageAdoption adoption = adoption(P1, "1", SourceKind.EXTERNAL_RECORD, receivedRecordId, T0, T1);

            Voyage voyage = Voyage.adoptedFrom(adoption);

            assertThat(voyage.voyageNumber()).isEqualTo("V2611E");
            assertHolds(voyage, adoption);
            assertThat(voyage.source().kind()).isEqualTo(SourceKind.EXTERNAL_RECORD);
            assertThat(voyage.source().reference()).isEqualTo(receivedRecordId);
            assertThat(voyage.aggregateVersion()).isZero();
        }

        static Stream<Arguments> newerAdoptions() {
            return Arrays.stream(SourceKind.values())
                    .flatMap(kind -> Stream.of(Arguments.of(kind, "1"), Arguments.of(kind, "2")));
        }

        @ParameterizedTest(name = "出典の種類 {0}・情報版 {1}")
        @MethodSource("newerAdoptions")
        void 行2_新しい公開した時刻のDE12ですべて置き換わり寄港の数も変わる(SourceKind kind, String infoVersion) {
            Voyage voyage = Voyage.adoptedFrom(adoption(THREE_CALLS, "1", T1));
            VoyageAdoption newer = adoption(P2, infoVersion, kind, HIGH_ID, T1, T2);

            assertThat(voyage.apply(newer)).isTrue();

            assertHolds(voyage, newer);
            assertThat(voyage.portCalls()).hasSize(2);
        }

        @ParameterizedTest(name = "古い方の情報版 {0}")
        @ValueSource(strings = {"1", "0"})
        void 行3_古い公開した時刻のDE12は適用せず届く順によらず新しい方が残る(String olderVersion) {
            VoyageAdoption older = adoption(P1, olderVersion, T1);
            VoyageAdoption newer = adoption(P2, "1", T2);

            Voyage newerFirst = Voyage.adoptedFrom(newer);
            assertThat(newerFirst.apply(older)).isFalse();
            assertHolds(newerFirst, newer);

            Voyage olderFirst = Voyage.adoptedFrom(older);
            assertThat(olderFirst.apply(newer)).isTrue();
            assertHolds(olderFirst, newer);
        }

        static Stream<Arguments> sameTimeAdoptions() {
            return Stream.of(
                    Arguments.of(
                            "取得時刻の新しい方",
                            adoption(P1, "1", SourceKind.EXTERNAL_RECORD, HIGH_ID, T0, T1),
                            adoption(P2, "1", SourceKind.EXTERNAL_RECORD, LOW_ID, T2, T1)),
                    Arguments.of(
                            "受信記録 ID の大きい方",
                            adoption(P1, "1", SourceKind.EXTERNAL_RECORD, LOW_ID, T0, T1),
                            adoption(P2, "1", SourceKind.EXTERNAL_RECORD, HIGH_ID, T0, T1)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sameTimeAdoptions")
        void 行4_同じ公開した時刻は順の規則で後ろの方が届く順によらず残る(String description, VoyageAdoption before, VoyageAdoption after) {
            Voyage voyageFirst = Voyage.adoptedFrom(before);
            assertThat(voyageFirst.apply(after)).as(description).isTrue();
            assertHolds(voyageFirst, after);

            Voyage reversed = Voyage.adoptedFrom(after);
            assertThat(reversed.apply(before)).as(description).isFalse();
            assertHolds(reversed, after);
        }

        @Test
        void 行4_完全に同じDE12は再配信なので適用しない() {
            VoyageAdoption adoption = adoption(P1, "1", SourceKind.EXTERNAL_RECORD, LOW_ID, T0, T1);
            Voyage voyage = Voyage.adoptedFrom(adoption);

            assertThat(voyage.apply(adoption)).isFalse();

            assertHolds(voyage, adoption);
        }

        @Test
        void 行5_同じ情報版で値の違う2つのDE12は届く順によらず後から公開した方が残る() {
            VoyageAdoption ingested = adoption(P1, "1", T1);
            VoyageAdoption decided = adoption(P2, "1", T2);

            assertHolds(applyInOrder(ingested, decided), decided);
            assertHolds(applyInOrder(decided, ingested), decided);
        }

        @Test
        void 行6_同じ情報版で同じ値のDE12は寄港を変えず公開した時刻と取得時刻と出典が新しくなる() {
            Voyage voyage = Voyage.adoptedFrom(adoption(P1, "1", SourceKind.EXTERNAL_RECORD, LOW_ID, T0, T1));
            VoyageAdoption sameValues = adoption(P1, "1", SourceKind.EXTERNAL_RECORD, HIGH_ID, T1, T2);

            assertThat(voyage.apply(sameValues)).isTrue();

            assertThat(voyage.portCalls()).isEqualTo(P1);
            assertHolds(voyage, sameValues);
        }

        @Test
        void 行7_同じ外部版を採用し直すと順のとおり最後の採用の値と公開した時刻が残る() {
            VoyageAdoption last = adoption(P1, "1", T2.plusSeconds(60));

            Voyage voyage = applyInOrder(adoption(P1, "1", T1), adoption(P2, "1", T2), last);

            assertHolds(voyage, last);
        }

        @Test
        void 行8_公開した時刻のない仮の航海はどのDE12より古く置き換わる() {
            Voyage provisional = new Voyage("V2611E", P1, "PROVISIONAL-1", new UtcInstant(T2));
            VoyageAdoption adoption = adoption(P2, "1", SourceKind.EXTERNAL_RECORD, LOW_ID, T0, T0);

            assertThat(provisional.publishedAt()).isEmpty();
            assertThat(provisional.apply(adoption)).isTrue();

            assertHolds(provisional, adoption);
        }

        @Test
        void 行46_外部版をまたいでもV2の取込の後にV1の隔離を採用すると届く順によらずV1の値が航海になる() {
            VoyageAdoption v2Ingested = adoption(P2, "2", T1);
            VoyageAdoption v1Adopted = adoption(P1, "1", T2);

            assertHolds(applyInOrder(v2Ingested, v1Adopted), v1Adopted);
            assertHolds(applyInOrder(v1Adopted, v2Ingested), v1Adopted);
        }
    }

    @Nested
    class Bolt39e_等しさと仮の航海 {

        @Test
        void 等しさは航海番号と寄港と採用情報版と取得時刻で比べ出典と公開した時刻と版は比べない() {
            Voyage provisional = new Voyage("V2611E", P1, "1", new UtcInstant(T0));
            Voyage adopted = Voyage.reconstitute(
                    "V2611E",
                    P1,
                    "1",
                    new Source(SourceKind.EXTERNAL_RECORD, HIGH_ID, new UtcInstant(T0)),
                    new UtcInstant(T2),
                    7);

            assertThat(adopted)
                    .isEqualTo(provisional)
                    .hasSameHashCodeAs(provisional)
                    .isNotEqualTo(new Voyage("V2611E", P2, "1", new UtcInstant(T0)));
        }

        @Test
        void 仮の航海の出典は手入力で版0() {
            Voyage provisional = new Voyage("V2611E", P1, "1", new UtcInstant(T0));

            assertThat(provisional.source().kind()).isEqualTo(SourceKind.MANUAL_ENTRY);
            assertThat(provisional.source().acquiredAt()).isEqualTo(new UtcInstant(T0));
            assertThat(provisional.aggregateVersion()).isZero();
        }
    }
}
