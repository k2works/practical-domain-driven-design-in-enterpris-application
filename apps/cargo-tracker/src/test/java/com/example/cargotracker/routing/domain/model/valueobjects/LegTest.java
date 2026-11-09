package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.UtcInstant;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 区間の値（寄港の位置の規則は Bolt 39f の行 3b）。 */
class LegTest {

    private static final Location TOKYO = new Location("JPTYO");
    private static final Location ROTTERDAM = new Location("NLRTM");
    private static final UtcInstant DEPARTURE = new UtcInstant(Instant.parse("2026-10-08T00:00:00Z"));
    private static final UtcInstant ARRIVAL = new UtcInstant(Instant.parse("2026-10-30T00:00:00Z"));
    private static final UtcInstant ACQUIRED = new UtcInstant(Instant.parse("2026-10-07T00:00:00Z"));

    private static Leg leg(Integer loadCallNo, Integer dischargeCallNo) {
        return new Leg("V-101", TOKYO, ROTTERDAM, DEPARTURE, ARRIVAL, "V-101@1", ACQUIRED, loadCallNo, dischargeCallNo);
    }

    @Nested
    class Bolt39f_寄港の位置 {

        @ParameterizedTest(name = "積地 {0}・揚地 {1}")
        @CsvSource(
                nullValues = "null",
                value = {"null, 2", "1, null", "2, 2", "3, 2"})
        void 行3b_片方だけの位置と積地が揚地より後の位置は拒否する(Integer loadCallNo, Integer dischargeCallNo) {
            assertThatThrownBy(() -> leg(loadCallNo, dischargeCallNo)).isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest(name = "積地 {0}・揚地 {1}")
        @CsvSource(
                nullValues = "null",
                value = {"null, null", "1, 2", "3, 4"})
        void 行3b_そろってないかそろって積地が小さい位置は受け付ける(Integer loadCallNo, Integer dischargeCallNo) {
            Leg leg = leg(loadCallNo, dischargeCallNo);

            assertThat(leg.loadCallNo()).isEqualTo(loadCallNo);
            assertThat(leg.dischargeCallNo()).isEqualTo(dischargeCallNo);
        }

        @Test
        void 行3b_7引数のコンストラクタは位置を持たない() {
            Leg leg = new Leg("V-101", TOKYO, ROTTERDAM, DEPARTURE, ARRIVAL, "V-101@1", ACQUIRED);

            assertThat(leg.loadCallNo()).isNull();
            assertThat(leg.dischargeCallNo()).isNull();
        }
    }
}
