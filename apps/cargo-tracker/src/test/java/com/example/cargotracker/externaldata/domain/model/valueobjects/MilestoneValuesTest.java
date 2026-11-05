package com.example.cargotracker.externaldata.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 主要実績の値（Bolt 39b2b）。追跡番号の値・実績の種類の値・場所・発生時刻。 */
class MilestoneValuesTest {

    private static final TrackingNumberValue TRACKING_NUMBER = new TrackingNumberValue("CTABCDEFGH2345");
    private static final Location TOKYO = new Location("JPTYO");
    private static final UtcInstant OCCURRED_AT = new UtcInstant(Instant.parse("2026-11-19T23:00:00Z"));

    @Test
    void 行24_主要実績の値は追跡番号と種類と場所と発生時刻を持つ() {
        MilestoneValues values = new MilestoneValues(TRACKING_NUMBER, MilestoneKindValue.DEPARTURE, TOKYO, OCCURRED_AT);

        assertThat(values.trackingNumber()).isEqualTo(TRACKING_NUMBER);
        assertThat(values.kind()).isEqualTo(MilestoneKindValue.DEPARTURE);
        assertThat(values.location()).isEqualTo(TOKYO);
        assertThat(values.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(values).isInstanceOf(RecordValues.class);
    }

    @Test
    void 行1_主要実績の要約は種類と場所と発生時刻をセミコロンで区切り時刻はUTCのマイクロ秒6桁にする() {
        MilestoneValues values = new MilestoneValues(
                TRACKING_NUMBER,
                MilestoneKindValue.DEPARTURE,
                TOKYO,
                new UtcInstant(Instant.parse("2026-11-19T23:00:00.000001Z")));

        assertThat(values.summary()).isEqualTo("DEPARTURE;JPTYO;2026-11-19T23:00:00.000001Z");
        assertThat(new MilestoneValues(TRACKING_NUMBER, MilestoneKindValue.ARRIVAL, TOKYO, OCCURRED_AT).summary())
                .isEqualTo("ARRIVAL;JPTYO;2026-11-19T23:00:00.000000Z");
    }

    @Test
    void 行24_主要実績の値はどの値のNULLも拒む() {
        MilestoneKindValue kind = MilestoneKindValue.DEPARTURE;

        assertThatThrownBy(() -> new MilestoneValues(null, kind, TOKYO, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MilestoneValues(TRACKING_NUMBER, null, TOKYO, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MilestoneValues(TRACKING_NUMBER, kind, null, OCCURRED_AT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MilestoneValues(TRACKING_NUMBER, kind, TOKYO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void Bolt39d3_行2_主要実績の対象のキーは追跡番号の値で受信記録の値はキーと要約を持つ() {
        MilestoneValues values = new MilestoneValues(TRACKING_NUMBER, MilestoneKindValue.DEPARTURE, TOKYO, OCCURRED_AT);

        assertThat(values.targetKey()).isEqualTo("CTABCDEFGH2345");
        assertThat(ReceivedValue.of(values))
                .isEqualTo(new ReceivedValue("CTABCDEFGH2345", "DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z"));
    }

    @Test
    void 行9_種類の誤りは行の誤りで6つの種類の名前を示す() {
        FormatDefect defect = FormatDefect.INVALID_KIND;

        assertThat(defect.occursIn(FormatDefect.Extent.ROW)).isTrue();
        assertThat(defect.occursIn(FormatDefect.Extent.FILE)).isFalse();
        assertThat(defect.rowNote(new PhysicalLineRange(7, 7), List.of("kind")))
                .isEqualTo("7 行目: 種類が違います（kind）。"
                        + "PICKUP・RECEIPT_AT_ORIGIN・DEPARTURE・TRANSSHIPMENT・ARRIVAL・DELIVERY のどれかで書いてください。");
    }

    /**
     * Bolt 39d3c の行 6・7（主要実績の要約から値に戻す。開始準備の決定 (2)、T-244）。往復の約束は航海と同じく「取込の値 {@code v} について
     * {@code ofSummary(v.targetKey(), v.summary())} が {@code v} と等しい」。規則に合わない要約は黙って読まずに {@link IllegalArgumentException}
     * （空の発生時刻も {@link NullPointerException} にしない）。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d3c_主要実績の要約から値に戻す {

        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.EnumSource(MilestoneKindValue.class)
        void 行6_主要実績の6種類の要約から戻した値は元の値と等しい(MilestoneKindValue kind) {
            MilestoneValues values = new MilestoneValues(
                    TRACKING_NUMBER,
                    kind,
                    new Location("USLAX"),
                    new UtcInstant(Instant.parse("2026-11-19T23:00:00.123456Z")));

            assertThat(values.summary()).isEqualTo(kind.name() + ";USLAX;2026-11-19T23:00:00.123456Z");
            assertThat(MilestoneValues.ofSummary(values.targetKey(), values.summary()))
                    .isEqualTo(values);
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.CsvSource(
                delimiter = '!',
                value = {
                    "区切りの数が違う ! CTABCDEFGH2345 ! DEPARTURE;JPTYO",
                    "区切りの数が多い ! CTABCDEFGH2345 ! DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z;",
                    "種類の名前がない ! CTABCDEFGH2345 ! UNKNOWN;JPTYO;2026-11-19T23:00:00.000000Z",
                    "場所が UN/LOCODE でない ! CTABCDEFGH2345 ! DEPARTURE;jptyo;2026-11-19T23:00:00.000000Z",
                    "発生時刻が読めない ! CTABCDEFGH2345 ! DEPARTURE;JPTYO;2026-11-19 23:00",
                    "発生時刻が空 ! CTABCDEFGH2345 ! DEPARTURE;JPTYO;",
                    "追跡番号の形が違う ! CT0000 ! DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z"
                })
        void 行7_規則に合わない主要実績の要約は黙って読まず不正な引数の例外にする(String name, String trackingNumber, String summary) {
            assertThatThrownBy(() -> MilestoneValues.ofSummary(trackingNumber, summary))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }

    /**
     * Bolt 39d3c の行 6（型で分ける入口。開始準備の決定 (3)、T-240・T-244）。{@code RecordValues} は interface でテストのクラスがないので、ここに
     * 置く。原本種別で航海か主要実績の要約から値に戻し、知らない原本種別は {@link IllegalArgumentException}。
     */
    @org.junit.jupiter.api.Nested
    class Bolt39d3c_型で分ける入口 {

        private static final String VOYAGE_SUMMARY =
                "JPTYO;;2026-11-19T23:00:00.000000Z|USLAX;2026-12-01T14:00:00.000000Z;";
        private static final String MILESTONE_SUMMARY = "DEPARTURE;JPTYO;2026-11-19T23:00:00.000000Z";

        @Test
        void 行6_航海は航海の値に主要実績は主要実績の値に戻す() {
            assertThat(RecordValues.ofSummary("VOYAGE", "V2611E", VOYAGE_SUMMARY))
                    .isEqualTo(VoyageValues.ofSummary("V2611E", VOYAGE_SUMMARY));
            assertThat(RecordValues.ofSummary("MILESTONE", "CTABCDEFGH2345", MILESTONE_SUMMARY))
                    .isEqualTo(new MilestoneValues(TRACKING_NUMBER, MilestoneKindValue.DEPARTURE, TOKYO, OCCURRED_AT));
        }

        /** 取込ファイルの種別（{@code CsvFileKind}）のすべての名前を入口が扱う（T-240）。 */
        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.EnumSource(
                com.example.cargotracker.externaldata.interfaces.file.CsvFileKind.class)
        void 行6_取込ファイルのすべての種別を入口が扱う(com.example.cargotracker.externaldata.interfaces.file.CsvFileKind kind) {
            String targetKey = kind.name().equals("VOYAGE") ? "V2611E" : "CTABCDEFGH2345";
            String summary = kind.name().equals("VOYAGE") ? VOYAGE_SUMMARY : MILESTONE_SUMMARY;

            RecordValues values = RecordValues.ofSummary(kind.name(), targetKey, summary);

            assertThat(values.targetKey()).isEqualTo(targetKey);
            assertThat(values.summary()).isEqualTo(summary);
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "「{0}」")
        @org.junit.jupiter.params.provider.ValueSource(strings = {"CONTAINER", "", "voyage"})
        void 行6_知らない原本種別は不正な引数の例外にする(String recordType) {
            assertThatThrownBy(() -> RecordValues.ofSummary(recordType, "V2611E", VOYAGE_SUMMARY))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
