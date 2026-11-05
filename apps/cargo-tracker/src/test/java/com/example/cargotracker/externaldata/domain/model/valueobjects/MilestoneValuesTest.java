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
}
