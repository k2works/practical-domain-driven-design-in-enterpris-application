package com.example.cargotracker.tracking.domain.model.valueobjects;

import static com.example.cargotracker.tracking.domain.model.TrackingFixture.at;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 公開の順（Bolt 39g4b の行 1。D-105）。リファクタリングで候補の規則から出した比べ方を縛る（出してから書くので Red にはならない）。入力と期待の組まで縛る（T-244）。
 */
class PublicationOrderTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "(a) 公開した時刻が新しい, 2026-11-19T11:00:00Z, 2026-11-19T08:00:00Z, rr-1, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-2, 1",
        "(b) 公開した時刻が同じで取得時刻が新しい, 2026-11-19T10:00:00Z, 2026-11-19T09:30:00Z, rr-1, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-2, 1",
        "(c) 両方同じで受信記録 ID が文字列の順で大きい, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-9, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-10, 1",
        "(d) 3 つとも同じ, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-9, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-9, 0",
        "(e) 公開した時刻が古く取得時刻が新しい, 2026-11-19T09:59:59Z, 2026-11-19T12:00:00Z, rr-9, 2026-11-19T10:00:00Z, 2026-11-19T09:00:00Z, rr-1, -1"
    })
    void 行1_公開の順は公開した時刻から取得時刻と受信記録IDの順に比べる(
            String name,
            String published,
            String acquired,
            String reference,
            String otherPublished,
            String otherAcquired,
            String otherReference,
            int expectedSign) {
        PublicationOrder order = new PublicationOrder(at(published), at(acquired), reference);
        PublicationOrder other = new PublicationOrder(at(otherPublished), at(otherAcquired), otherReference);

        assertThat(Integer.signum(order.compareTo(other))).isEqualTo(expectedSign);
        assertThat(Integer.signum(other.compareTo(order))).isEqualTo(-expectedSign);
        assertThat(order.isNewerThan(other)).isEqualTo(expectedSign > 0);
    }
}
