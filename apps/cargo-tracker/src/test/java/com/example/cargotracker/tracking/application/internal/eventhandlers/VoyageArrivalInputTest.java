package com.example.cargotracker.tracking.application.internal.eventhandlers;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Location;
import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import com.example.cargotracker.tracking.domain.model.valueobjects.PublicationOrder;
import com.example.cargotracker.tracking.domain.model.valueobjects.VoyagePortCall;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DE-12 の入力の検査と変換（Bolt 40-1 の M3。旧 {@code VoyageArrivalTest} の行 1a・1b・1 をここへ移した）。listener の側の ERROR のログは
 * {@code VoyageArrivalEventHandlerTest} の行 13 (c) が確かめ、ここでは欠けの名前を確かめる。出典の参照の空と取得時刻の null は共有カーネルの
 * {@link Source} の作り方が拒む（{@code SourceTest}）。
 */
class VoyageArrivalInputTest {

    private static final UtcInstant PUBLISHED_AT = at("2026-11-06T02:00:00Z");
    private static final UtcInstant ACQUIRED_AT = at("2026-11-06T01:00:00Z");
    private static final Source SOURCE = new Source(SourceKind.EXTERNAL_RECORD, "rr-1", ACQUIRED_AT);

    @Test
    void 行1a_公開した時刻がなければ内容の欠け() {
        assertThatThrownBy(() -> VoyageArrivalInput.orderOf(null, SOURCE))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class)
                .hasMessageEndingWith(": publishedAt");
    }

    @Test
    void 行1b_出典がなければ内容の欠け() {
        assertThatThrownBy(() -> VoyageArrivalInput.orderOf(PUBLISHED_AT, null))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class)
                .hasMessageEndingWith(": source");
    }

    @Test
    void 行1_そろっていれば出典の取得時刻と参照で公開の順を作る() {
        assertThat(VoyageArrivalInput.orderOf(PUBLISHED_AT, SOURCE))
                .isEqualTo(new PublicationOrder(PUBLISHED_AT, ACQUIRED_AT, "rr-1"));
    }

    @Test
    void 航海番号が空なら内容の欠け() {
        assertThatThrownBy(() -> VoyageArrivalInput.voyageNumberOf(" "))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class)
                .hasMessageEndingWith(": voyageNumber");
        assertThatThrownBy(() -> VoyageArrivalInput.voyageNumberOf(null))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class);
        assertThat(VoyageArrivalInput.voyageNumberOf("V200")).isEqualTo("V200");
    }

    @Test
    void 寄港の並びが下限より少なければ内容の欠け() {
        VoyagePortCall call = VoyagePortCall.of(new Location("KRPUS"), null, at("2026-11-04T00:00:00Z"));

        assertThatThrownBy(() -> VoyageArrivalInput.portCallsOf(List.of(call)))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class)
                .hasMessageEndingWith(": portCalls");
        assertThat(VoyageArrivalInput.portCallsOf(List.of(call, call))).hasSize(VoyageArrivalInput.MIN_PORT_CALLS);
    }

    @Test
    void 寄港の港がなければ内容の欠け() {
        assertThatThrownBy(() -> VoyageArrivalInput.portCallOf(null, null, null))
                .isInstanceOf(VoyageArrivalInput.InvalidEventValueException.class)
                .hasMessageEndingWith(": portCalls.port");
    }
}
