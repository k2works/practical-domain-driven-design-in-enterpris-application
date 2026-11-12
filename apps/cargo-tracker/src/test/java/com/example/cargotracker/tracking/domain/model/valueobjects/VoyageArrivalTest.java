package com.example.cargotracker.tracking.domain.model.valueobjects;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.Source;
import com.example.cargotracker.shared.domain.SourceKind;
import com.example.cargotracker.shared.domain.UtcInstant;
import org.junit.jupiter.api.Test;

/**
 * 航海の到着予定の値の作り方（Bolt 39g4c の行 1 (a)(b)。Bolt 39g4b の終了報告の議題 3 の返済）。listener の側の ERROR のログは
 * {@code VoyageArrivalEventHandlerTest} の行 13 (c) が確かめ、ここでは値の側の欠けの名前を確かめる。出典の参照の空と取得時刻の null は
 * 共有カーネルの {@link Source} の作り方が拒む（{@code SourceTest}）。
 */
class VoyageArrivalTest {

    private static final UtcInstant PUBLISHED_AT = at("2026-11-06T02:00:00Z");
    private static final UtcInstant ACQUIRED_AT = at("2026-11-06T01:00:00Z");
    private static final Source SOURCE = new Source(SourceKind.EXTERNAL_RECORD, "rr-1", ACQUIRED_AT);

    @Test
    void 行1a_公開した時刻がなければ内容の欠け() {
        assertThatThrownBy(() -> VoyageArrival.orderOf(null, SOURCE))
                .isInstanceOf(VoyageArrival.InvalidEventValueException.class)
                .hasMessageEndingWith(": publishedAt");
    }

    @Test
    void 行1b_出典がなければ内容の欠け() {
        assertThatThrownBy(() -> VoyageArrival.orderOf(PUBLISHED_AT, null))
                .isInstanceOf(VoyageArrival.InvalidEventValueException.class)
                .hasMessageEndingWith(": source");
    }

    @Test
    void 行1_そろっていれば出典の取得時刻と参照で公開の順を作る() {
        assertThat(VoyageArrival.orderOf(PUBLISHED_AT, SOURCE))
                .isEqualTo(new PublicationOrder(PUBLISHED_AT, ACQUIRED_AT, "rr-1"));
    }
}
